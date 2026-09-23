package com.routeflow.service;

import com.routeflow.config.AppClock;
import com.routeflow.domain.Route;
import com.routeflow.domain.RouteStop;
import com.routeflow.domain.Vehicle;
import com.routeflow.domain.enums.OrderStatus;
import com.routeflow.domain.enums.RouteStatus;
import com.routeflow.dto.route.ReassignStopRequest;
import com.routeflow.dto.route.ReorderRequest;
import com.routeflow.exception.BadRequestException;
import com.routeflow.exception.ResourceNotFoundException;
import com.routeflow.repository.RouteRepository;
import com.routeflow.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DispatchService {

    private final RouteRepository routeRepository;
    private final VehicleRepository vehicleRepository;
    private final RoutingEngineService routingEngineService;
    private final OrderService orderService;
    private final AppClock clock;

    public Route findById(String id) {
        return routeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Route not found: " + id));
    }

    public List<Route> findByDate(java.time.LocalDate date) {
        return routeRepository.findByDate(date);
    }

    /**
     * Pushes a planned route to its driver. ETAs are re-based on "now" (or the shift start if that
     * is still in the future): the plan was drawn up assuming an on-time start, but a route
     * dispatched at 15:00 must not show - and be judged against - a 09:20 ETA.
     */
    public Route dispatch(String routeId) {
        Route route = findById(routeId);
        if (route.getStatus() != RouteStatus.PLANNED) {
            throw new BadRequestException("Only PLANNED routes can be dispatched");
        }

        Vehicle vehicle = vehicleFor(route);
        Instant shiftStart = clock.at(route.getDate(), vehicle.getShiftStart());
        Instant now = Instant.now();
        recomputeEtas(route, vehicle, now.isAfter(shiftStart) ? now : shiftStart);

        route.setStatus(RouteStatus.DISPATCHED);
        route.setDispatchedAt(Instant.now());
        route = routeRepository.save(route);

        for (RouteStop stop : route.getStops()) {
            orderService.updateStatus(stop.getOrderId(), OrderStatus.ASSIGNED, null);
        }
        return route;
    }

    /** Reorder the stops within a single planned route and recompute ETAs + geometry. */
    public Route reorder(String routeId, ReorderRequest request) {
        Route route = findById(routeId);
        requirePlanned(route);

        Map<String, RouteStop> byId = route.getStops().stream()
                .collect(Collectors.toMap(RouteStop::getId, s -> s));

        if (byId.size() != request.orderedStopIds().size() || !byId.keySet().containsAll(request.orderedStopIds())) {
            throw new BadRequestException("orderedStopIds must contain exactly the route's existing stop ids");
        }

        int seq = 1;
        for (String stopId : request.orderedStopIds()) {
            byId.get(stopId).setSequence(seq++);
        }

        recomputeEtas(route, vehicleFor(route), shiftStartOf(route));
        return routeRepository.save(route);
    }

    /** Move a stop to a different planned route (vehicle), appending it (or at the given sequence) and recomputing both. */
    public void reassignStop(String stopId, ReassignStopRequest request) {
        Route sourceRoute = routeRepository.findByStopsId(stopId)
                .orElseThrow(() -> new ResourceNotFoundException("Stop not found: " + stopId));
        Route targetRoute = findById(request.targetRouteId());

        requirePlanned(sourceRoute);
        requirePlanned(targetRoute);
        if (sourceRoute.getId().equals(targetRoute.getId())) {
            throw new BadRequestException("Stop is already on that route - use reorder instead");
        }

        RouteStop stop = sourceRoute.getStops().stream()
                .filter(s -> s.getId().equals(stopId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Stop not found: " + stopId));

        sourceRoute.getStops().remove(stop);
        renumber(sourceRoute);

        int insertAt = request.sequence() != null
                ? Math.max(0, Math.min(request.sequence() - 1, targetRoute.getStops().size()))
                : targetRoute.getStops().size();
        targetRoute.getStops().add(insertAt, stop);
        renumber(targetRoute);

        recomputeEtas(targetRoute, vehicleFor(targetRoute), shiftStartOf(targetRoute));
        routeRepository.save(targetRoute);

        if (sourceRoute.getStops().isEmpty()) {
            routeRepository.delete(sourceRoute); // an empty route has nothing to dispatch
        } else {
            recomputeEtas(sourceRoute, vehicleFor(sourceRoute), shiftStartOf(sourceRoute));
            routeRepository.save(sourceRoute);
        }
    }

    private void requirePlanned(Route route) {
        if (route.getStatus() != RouteStatus.PLANNED) {
            throw new BadRequestException("Only PLANNED (not yet dispatched) routes can be re-planned");
        }
    }

    private Vehicle vehicleFor(Route route) {
        return vehicleRepository.findById(route.getVehicleId())
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found: " + route.getVehicleId()));
    }

    private Instant shiftStartOf(Route route) {
        return clock.at(route.getDate(), vehicleFor(route).getShiftStart());
    }

    private void renumber(Route route) {
        int seq = 1;
        for (RouteStop stop : route.getStops()) {
            stop.setSequence(seq++);
        }
    }

    /** Recomputes planned ETAs, totals and the map geometry for the route's current stop order, from the vehicle depot. */
    private void recomputeEtas(Route route, Vehicle vehicle, Instant startAt) {
        route.getStops().sort((a, b) -> Integer.compare(a.getSequence(), b.getSequence()));

        RoutingEngineService.Point current = new RoutingEngineService.Point(
                vehicle.getStartDepotLat(), vehicle.getStartDepotLng());
        Instant currentTime = startAt;

        double totalDistanceKm = 0;
        double totalDurationMin = 0;
        List<RoutingEngineService.Point> polyline = new ArrayList<>();
        polyline.add(current);

        for (RouteStop stop : route.getStops()) {
            RoutingEngineService.Point to = new RoutingEngineService.Point(stop.getLat(), stop.getLng());
            RoutingEngineService.LegResult leg = routingEngineService.route(current, to);

            Instant arrival = currentTime.plusSeconds((long) (leg.durationMin() * 60));
            if (stop.getTimeWindowStart() != null && arrival.isBefore(stop.getTimeWindowStart())) {
                arrival = stop.getTimeWindowStart();
            }

            stop.setPlannedEta(arrival);
            stop.setDistanceFromPrevKm(Math.round(leg.distanceKm() * 100.0) / 100.0);

            totalDistanceKm += leg.distanceKm();
            totalDurationMin += leg.durationMin();
            polyline.addAll(leg.polyline());

            current = to;
            currentTime = arrival.plusSeconds(5 * 60);
        }

        route.setPlannedDistanceKm(Math.round(totalDistanceKm * 100.0) / 100.0);
        route.setPlannedDurationMin(Math.round(totalDurationMin * 100.0) / 100.0);
        route.setPolyline(RoutingEngineService.encodeSimplePolyline(polyline));
    }
}
