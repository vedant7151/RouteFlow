package com.routeflow.service;

import com.routeflow.domain.Route;
import com.routeflow.domain.RouteStop;
import com.routeflow.domain.enums.OrderStatus;
import com.routeflow.domain.enums.RouteStatus;
import com.routeflow.domain.enums.StopStatus;
import com.routeflow.dto.stop.StopStatusUpdateRequest;
import com.routeflow.exception.BadRequestException;
import com.routeflow.exception.ResourceNotFoundException;
import com.routeflow.repository.RouteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

/**
 * RouteStop is embedded inside its parent Route document (Mongo has no joins/foreign keys),
 * so every stop operation here loads the owning Route, mutates the stop within its "stops"
 * list, and saves the whole Route back. The matching Order's status is updated as a separate,
 * explicit write via OrderService - Mongo has no JPA-style dirty-checking/cascade, so nothing
 * persists unless it's saved directly.
 *
 * Also owns the route lifecycle: the first stop update on a DISPATCHED route moves it to
 * IN_PROGRESS, and once every stop is DELIVERED/FAILED/SKIPPED the route becomes COMPLETED.
 */
@Service
@RequiredArgsConstructor
public class StopService {

    private static final Map<StopStatus, OrderStatus> STOP_TO_ORDER_STATUS = Map.of(
            StopStatus.PENDING, OrderStatus.ASSIGNED,
            StopStatus.EN_ROUTE, OrderStatus.EN_ROUTE,
            StopStatus.ARRIVED, OrderStatus.ARRIVED,
            StopStatus.DELIVERED, OrderStatus.DELIVERED,
            StopStatus.FAILED, OrderStatus.FAILED,
            StopStatus.SKIPPED, OrderStatus.FAILED
    );

    private final RouteRepository routeRepository;
    private final OrderService orderService;

    public RouteStop findById(String id) {
        Route route = findOwningRoute(id);
        return stopWithin(route, id);
    }

    public RouteStop updateStatus(String stopId, StopStatusUpdateRequest request) {
        Route route = findOwningRoute(stopId);
        RouteStop stop = stopWithin(route, stopId);

        if (route.getStatus() == RouteStatus.PLANNED) {
            throw new BadRequestException("Dispatch the route before updating its stops");
        }
        if (isTerminal(stop.getStatus())) {
            if (stop.getStatus() == request.status()) {
                return stop; // idempotent retry (e.g. a flaky mobile connection resending the same update)
            }
            throw new BadRequestException("Stop is already " + stop.getStatus() + " and can't change to " + request.status());
        }

        stop.setStatus(request.status());
        if (stop.getActualArrival() == null && (request.status() == StopStatus.ARRIVED
                || request.status() == StopStatus.DELIVERED || request.status() == StopStatus.FAILED)) {
            stop.setActualArrival(Instant.now());
        }

        if (route.getStatus() == RouteStatus.DISPATCHED) {
            route.setStatus(RouteStatus.IN_PROGRESS);
        }
        if (route.getStops().stream().allMatch(s -> isTerminal(s.getStatus()))) {
            route.setStatus(RouteStatus.COMPLETED);
        }
        routeRepository.save(route);

        String reason = (request.status() == StopStatus.FAILED || request.status() == StopStatus.SKIPPED)
                ? request.exceptionReason() : null;
        orderService.updateStatus(stop.getOrderId(), STOP_TO_ORDER_STATUS.get(request.status()), reason);

        return stop;
    }

    /** Explicit "driver starts the route" action: DISPATCHED -> IN_PROGRESS. */
    public Route startRoute(String routeId) {
        Route route = routeRepository.findById(routeId)
                .orElseThrow(() -> new ResourceNotFoundException("Route not found: " + routeId));
        if (route.getStatus() == RouteStatus.DISPATCHED) {
            route.setStatus(RouteStatus.IN_PROGRESS);
            return routeRepository.save(route);
        }
        if (route.getStatus() == RouteStatus.PLANNED) {
            throw new BadRequestException("This route hasn't been dispatched yet");
        }
        return route;
    }

    static boolean isTerminal(StopStatus status) {
        return status == StopStatus.DELIVERED || status == StopStatus.FAILED || status == StopStatus.SKIPPED;
    }

    private Route findOwningRoute(String stopId) {
        return routeRepository.findByStopsId(stopId)
                .orElseThrow(() -> new ResourceNotFoundException("Stop not found: " + stopId));
    }

    private RouteStop stopWithin(Route route, String stopId) {
        return route.getStops().stream()
                .filter(s -> s.getId().equals(stopId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Stop not found: " + stopId));
    }
}
