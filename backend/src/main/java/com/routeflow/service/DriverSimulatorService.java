package com.routeflow.service;

import com.routeflow.domain.Route;
import com.routeflow.domain.RouteStop;
import com.routeflow.domain.Vehicle;
import com.routeflow.domain.enums.RouteStatus;
import com.routeflow.domain.enums.StopStatus;
import com.routeflow.dto.stop.StopStatusUpdateRequest;
import com.routeflow.dto.tracking.LocationPingMessage;
import com.routeflow.exception.ResourceNotFoundException;
import com.routeflow.repository.RouteRepository;
import com.routeflow.repository.VehicleRepository;
import com.routeflow.util.GeoUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side driver mover (PRD 6.3 "Driver simulator"). Moves every DISPATCHED/IN_PROGRESS
 * route's vehicle stop-to-stop so the whole system is demoable without real driver phones.
 * Purely additive: a real driver's browser Geolocation stream (POST/STOMP to the same
 * TrackingService.ingest) works exactly the same way and simply supersedes the simulated pings.
 *
 * Auto-completes each stop as DELIVERED a few ticks after "arrival" for demo flow; real drivers
 * complete a stop through the driver web view with proof-of-delivery instead.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DriverSimulatorService {

    private static final double ARRIVAL_THRESHOLD_KM = 0.05; // ~50m counts as "arrived"

    private final RouteRepository routeRepository;
    private final VehicleRepository vehicleRepository;
    private final TrackingService trackingService;
    private final StopService stopService;

    @Value("${routeflow.simulator.enabled}")
    private boolean enabled;

    @Value("${routeflow.routing.fallback-avg-speed-kmh}")
    private double avgSpeedKmh;

    @Value("${routeflow.simulator.tick-ms}")
    private long tickMs;

    @Value("${routeflow.simulator.speed-multiplier:1}")
    private double speedMultiplier;

    @Value("${routeflow.simulator.dwell-ticks:3}")
    private int dwellTicksBeforeAutoDeliver;

    @Value("${routeflow.simulator.real-driver-timeout-seconds:20}")
    private long realDriverTimeoutSeconds;

    private final Map<String, SimState> stateByVehicle = new ConcurrentHashMap<>();

    private record SimState(double lat, double lng, int stopIndex, int dwellTicks) {
    }

    @Scheduled(fixedRateString = "${routeflow.simulator.tick-ms}")
    public void tick() {
        if (!enabled) {
            return;
        }

        List<Route> activeRoutes = routeRepository.findAll().stream()
                .filter(r -> r.getStatus() == RouteStatus.DISPATCHED || r.getStatus() == RouteStatus.IN_PROGRESS)
                .toList();

        for (Route route : activeRoutes) {
            try {
                simulateRoute(route);
            } catch (Exception ex) {
                log.warn("Driver simulator tick failed for route {}: {}", route.getId(), ex.getMessage());
            }
        }
    }

    private void simulateRoute(Route route) {
        Vehicle vehicle = vehicleRepository.findById(route.getVehicleId())
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found: " + route.getVehicleId()));

        // A real driver's phone is streaming GPS for this vehicle - it supersedes the simulator.
        if (trackingService.hasRecentRealPing(vehicle.getId(), Duration.ofSeconds(realDriverTimeoutSeconds))) {
            return;
        }

        if (route.getStatus() == RouteStatus.DISPATCHED) {
            route.setStatus(RouteStatus.IN_PROGRESS);
            routeRepository.save(route);
        }

        List<RouteStop> stops = route.getStops();
        String vehicleId = vehicle.getId();

        int nextIndex = firstUndeliveredIndex(stops);
        if (nextIndex < 0) {
            if (route.getStatus() != RouteStatus.COMPLETED) {
                route.setStatus(RouteStatus.COMPLETED);
                routeRepository.save(route);
            }
            stateByVehicle.remove(vehicleId);
            return;
        }

        RouteStop target = stops.get(nextIndex);
        SimState state = stateByVehicle.computeIfAbsent(vehicleId, id -> new SimState(
                vehicle.getStartDepotLat(), vehicle.getStartDepotLng(), nextIndex, 0));

        // Route was reassigned/reordered under us - reset to the (new) current target.
        if (state.stopIndex() != nextIndex) {
            state = new SimState(state.lat(), state.lng(), nextIndex, 0);
        }

        double targetLat = target.getLat();
        double targetLng = target.getLng();
        double distanceKm = GeoUtils.haversineKm(state.lat(), state.lng(), targetLat, targetLng);

        if (distanceKm <= ARRIVAL_THRESHOLD_KM) {
            // Mongo has no shared persistence-context caching, so re-read the status each call
            // returns instead of relying on the (now possibly stale) in-memory `target` copy.
            StopStatus currentStatus = target.getStatus();
            if (currentStatus == StopStatus.PENDING || currentStatus == StopStatus.EN_ROUTE) {
                currentStatus = stopService.updateStatus(target.getId(), new StopStatusUpdateRequest(
                        StopStatus.ARRIVED, null, targetLat, targetLng)).getStatus();
            }

            int dwell = state.dwellTicks() + 1;
            if (currentStatus == StopStatus.ARRIVED && dwell >= dwellTicksBeforeAutoDeliver) {
                stopService.updateStatus(target.getId(), new StopStatusUpdateRequest(
                        StopStatus.DELIVERED, null, targetLat, targetLng));
            }

            publishPing(vehicleId, targetLat, targetLng);
            stateByVehicle.put(vehicleId, new SimState(targetLat, targetLng, nextIndex, dwell));
            return;
        }

        if (target.getStatus() == StopStatus.PENDING) {
            stopService.updateStatus(target.getId(), new StopStatusUpdateRequest(
                    StopStatus.EN_ROUTE, null, state.lat(), state.lng()));
        }

        double stepKm = avgSpeedKmh * speedMultiplier * (tickMs / 3_600_000.0);
        double fraction = Math.min(1.0, stepKm / Math.max(distanceKm, 0.0001));
        double newLat = state.lat() + (targetLat - state.lat()) * fraction;
        double newLng = state.lng() + (targetLng - state.lng()) * fraction;

        publishPing(vehicleId, newLat, newLng);
        stateByVehicle.put(vehicleId, new SimState(newLat, newLng, nextIndex, 0));
    }

    private int firstUndeliveredIndex(List<RouteStop> stops) {
        for (int i = 0; i < stops.size(); i++) {
            StopStatus status = stops.get(i).getStatus();
            if (status != StopStatus.DELIVERED && status != StopStatus.FAILED && status != StopStatus.SKIPPED) {
                return i;
            }
        }
        return -1;
    }

    private void publishPing(String vehicleId, double lat, double lng) {
        trackingService.ingest(new LocationPingMessage(vehicleId, lat, lng, avgSpeedKmh, Instant.now()), true);
    }
}
