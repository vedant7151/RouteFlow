package com.routeflow.service;

import com.routeflow.domain.LocationPing;
import com.routeflow.domain.Route;
import com.routeflow.domain.RouteStop;
import com.routeflow.domain.Vehicle;
import com.routeflow.domain.enums.RouteStatus;
import com.routeflow.dto.tracking.LocationPingMessage;
import com.routeflow.dto.tracking.VehicleLocationEvent;
import com.routeflow.exception.ResourceNotFoundException;
import com.routeflow.repository.LocationPingRepository;
import com.routeflow.repository.RouteRepository;
import com.routeflow.repository.VehicleRepository;
import com.routeflow.util.GeoUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ingests driver location pings (from the browser Geolocation API or the server-side simulator)
 * and fans them out to dispatcher clients over STOMP topic /topic/vehicles/{vehicleId}.
 * Ingestion is intentionally lightweight so it does not add latency to the "live within ~2s"
 * requirement. Each broadcast also carries rolling ETAs for the remaining stops.
 */
@Service
@RequiredArgsConstructor
public class TrackingService {

    /** Straight-line distance x this ~ road distance, for ETAs that must be cheap enough to run per ping. */
    private static final double ROAD_FACTOR = 1.3;
    private static final long HANDLING_SECONDS = 5 * 60;

    private final VehicleRepository vehicleRepository;
    private final LocationPingRepository locationPingRepository;
    private final RouteRepository routeRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Value("${routeflow.routing.fallback-avg-speed-kmh}")
    private double avgSpeedKmh;

    @Value("${routeflow.simulator.speed-multiplier:1}")
    private double simulatorSpeedMultiplier;

    private final Map<String, Instant> lastRealPingByVehicle = new ConcurrentHashMap<>();

    /** Ping from a real device (driver web view). */
    public void ingest(LocationPingMessage message) {
        ingest(message, false);
    }

    public void ingest(LocationPingMessage message, boolean simulated) {
        Vehicle vehicle = vehicleRepository.findById(message.vehicleId())
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found: " + message.vehicleId()));

        Instant timestamp = message.timestamp() != null ? message.timestamp() : Instant.now();
        if (!simulated) {
            lastRealPingByVehicle.put(vehicle.getId(), Instant.now());
        }

        locationPingRepository.save(LocationPing.builder()
                .vehicleId(vehicle.getId())
                .lat(message.lat())
                .lng(message.lng())
                .speed(message.speed())
                .timestamp(timestamp)
                .build());

        String activeRouteId = null;
        String currentStopId = null;
        String currentStopStatus = null;
        List<VehicleLocationEvent.StopEta> etas = List.of();

        Optional<Route> activeRoute = routeRepository.findByVehicleId(vehicle.getId()).stream()
                .filter(r -> r.getStatus() == RouteStatus.DISPATCHED || r.getStatus() == RouteStatus.IN_PROGRESS)
                .findFirst();

        if (activeRoute.isPresent()) {
            Route route = activeRoute.get();
            activeRouteId = route.getId();
            List<RouteStop> remaining = route.getStops().stream()
                    .filter(s -> !StopService.isTerminal(s.getStatus()))
                    .toList();
            if (!remaining.isEmpty()) {
                currentStopId = remaining.get(0).getId();
                currentStopStatus = remaining.get(0).getStatus().name();
                etas = rollingEtas(message.lat(), message.lng(), remaining, simulated);
            }
        }

        VehicleLocationEvent event = new VehicleLocationEvent(
                vehicle.getId(), message.lat(), message.lng(), message.speed(), timestamp,
                activeRouteId, currentStopId, currentStopStatus, simulated, etas
        );

        messagingTemplate.convertAndSend("/topic/vehicles/" + vehicle.getId(), event);
        messagingTemplate.convertAndSend("/topic/vehicles", event); // fleet-wide feed for the dispatcher map
    }

    /** True if a real device pinged this vehicle within the window - the simulator then stands down. */
    public boolean hasRecentRealPing(String vehicleId, Duration window) {
        Instant last = lastRealPingByVehicle.get(vehicleId);
        return last != null && last.isAfter(Instant.now().minus(window));
    }

    public Optional<LocationPing> latestFor(String vehicleId) {
        return locationPingRepository.findFirstByVehicleIdOrderByTimestampDesc(vehicleId);
    }

    List<VehicleLocationEvent.StopEta> rollingEtas(double lat, double lng, List<RouteStop> remaining, boolean simulated) {
        // The simulator fast-forwards time, so its ETAs use the same virtual speed it moves at.
        double speed = Math.max(1.0, simulated ? avgSpeedKmh * simulatorSpeedMultiplier : avgSpeedKmh);

        List<VehicleLocationEvent.StopEta> etas = new ArrayList<>();
        Instant clock = Instant.now();
        double curLat = lat;
        double curLng = lng;

        for (RouteStop stop : remaining) {
            double km = GeoUtils.haversineKm(curLat, curLng, stop.getLat(), stop.getLng()) * ROAD_FACTOR;
            Instant arrival = clock.plusSeconds((long) (km / speed * 3600));
            if (stop.getTimeWindowStart() != null && arrival.isBefore(stop.getTimeWindowStart())) {
                arrival = stop.getTimeWindowStart();
            }
            etas.add(new VehicleLocationEvent.StopEta(stop.getId(), arrival));
            clock = arrival.plusSeconds(simulated ? HANDLING_SECONDS / 10 : HANDLING_SECONDS);
            curLat = stop.getLat();
            curLng = stop.getLng();
        }
        return etas;
    }
}
