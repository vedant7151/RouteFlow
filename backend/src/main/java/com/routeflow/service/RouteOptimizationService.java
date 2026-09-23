package com.routeflow.service;

import com.routeflow.config.AppClock;
import com.routeflow.domain.Order;
import com.routeflow.domain.Route;
import com.routeflow.domain.RouteStop;
import com.routeflow.domain.Vehicle;
import com.routeflow.domain.enums.OrderStatus;
import com.routeflow.domain.enums.RouteStatus;
import com.routeflow.domain.enums.StopStatus;
import com.routeflow.dto.route.OptimizeRequest;
import com.routeflow.dto.route.OptimizeResponse;
import com.routeflow.dto.route.RouteResponse;
import com.routeflow.exception.BadRequestException;
import com.routeflow.repository.OrderRepository;
import com.routeflow.repository.RouteRepository;
import com.routeflow.repository.VehicleRepository;
import com.routeflow.util.GeoUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Vehicle Routing Problem heuristic: cluster-first, route-second.
 * <ol>
 *   <li><b>Cluster</b>: urgent, then heavy, orders are given to the nearest depot that still has capacity.</li>
 *   <li><b>Sequence</b>: each vehicle builds its route by repeatedly taking the best feasible next stop,
 *       scored as road-km minus a bonus per priority level (so urgent orders are pulled forward without
 *       zig-zagging the vehicle across the city).</li>
 *   <li><b>Leftovers</b>: anything a vehicle's own cluster could not fit (time window, shift) is offered
 *       to every vehicle again.</li>
 * </ol>
 * Feasibility honours vehicle capacity, per-order time windows (waiting for the window to open,
 * rejecting stops that would arrive after it closes) and the driver's shift end. It is a dependency-free
 * pure-Java stand-in for OR-Tools/jsprit; only the {@link OptimizeResponse} contract is depended on elsewhere.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RouteOptimizationService {

    /** One priority level is worth this many km of detour when choosing the next stop. */
    static final double PRIORITY_BONUS_KM = 4.0;
    /** Idle minutes spent waiting for a time window to open, expressed as km of detour (so don't wait if you can drive elsewhere). */
    static final double WAIT_PENALTY_KM_PER_MIN = 0.15;
    /** A window closing within this many minutes of arrival starts pulling the stop forward... */
    static final double DEADLINE_HORIZON_MIN = 90;
    /** ...by up to this many km-equivalents when the window is about to close. */
    static final double DEADLINE_BONUS_KM = 12.0;
    private static final long HANDLING_SECONDS = 5 * 60;

    private final OrderRepository orderRepository;
    private final VehicleRepository vehicleRepository;
    private final RouteRepository routeRepository;
    private final RoutingEngineService routingEngineService;
    private final AppClock clock;

    public OptimizeResponse optimize(OptimizeRequest request) {
        List<Vehicle> vehicles = resolveVehicles(request.vehicleIds());
        if (vehicles.isEmpty()) {
            throw new BadRequestException("No active vehicles available to optimize");
        }

        List<Order> unassigned = resolveCandidateOrders(request.date());
        List<Order> assignedThisRun = new ArrayList<>();

        Map<Vehicle, List<Order>> clusters = clusterByNearestDepot(vehicles, unassigned);
        Map<Vehicle, RouteBuilder> builders = new LinkedHashMap<>();
        for (Vehicle vehicle : vehicles) {
            builders.put(vehicle, new RouteBuilder(vehicle, request.date()));
        }

        // Phase 2: each vehicle sequences its own cluster.
        for (Vehicle vehicle : vehicles) {
            builders.get(vehicle).fill(clusters.get(vehicle), unassigned, assignedThisRun);
        }
        // Phase 3: whatever is left goes to whoever can still take it.
        for (Vehicle vehicle : vehicles) {
            builders.get(vehicle).fill(new ArrayList<>(unassigned), unassigned, assignedThisRun);
        }

        List<Route> builtRoutes = new ArrayList<>();
        for (RouteBuilder builder : builders.values()) {
            Route route = builder.finish();
            if (!route.getStops().isEmpty()) {
                builtRoutes.add(routeRepository.save(route));
            }
        }

        // Mongo has no JPA dirty-checking - orders flipped to ASSIGNED above must be explicitly persisted.
        if (!assignedThisRun.isEmpty()) {
            orderRepository.saveAll(assignedThisRun);
        }

        List<String> unassignedIds = unassigned.stream().map(Order::getId).toList();
        if (!unassignedIds.isEmpty()) {
            log.info("Route optimization left {} orders unassigned: {}", unassignedIds.size(), unassignedIds);
        }

        List<RouteResponse> responses = builtRoutes.stream().map(RouteResponse::from).toList();
        return new OptimizeResponse(responses, unassignedIds);
    }

    /** Discards a not-yet-dispatched plan and returns its orders to PENDING so they can be re-optimized. */
    public void discardPlannedRoute(String routeId) {
        Route route = routeRepository.findById(routeId)
                .orElseThrow(() -> new com.routeflow.exception.ResourceNotFoundException("Route not found: " + routeId));
        if (route.getStatus() != RouteStatus.PLANNED) {
            throw new BadRequestException("Only PLANNED routes can be discarded");
        }
        List<Order> orders = orderRepository.findAllById(
                route.getStops().stream().map(RouteStop::getOrderId).toList());
        orders.forEach(o -> o.setStatus(OrderStatus.PENDING));
        orderRepository.saveAll(orders);
        routeRepository.delete(route);
    }

    private List<Vehicle> resolveVehicles(List<String> vehicleIds) {
        if (vehicleIds == null || vehicleIds.isEmpty()) {
            return vehicleRepository.findByActiveTrue();
        }
        return vehicleRepository.findAllById(vehicleIds).stream()
                .filter(Vehicle::isActive)
                .toList();
    }

    private List<Order> resolveCandidateOrders(java.time.LocalDate date) {
        Instant from = clock.startOfDay(date);
        Instant to = clock.startOfDay(date.plusDays(1));

        return orderRepository.findByStatus(OrderStatus.PENDING).stream()
                .filter(o -> o.getLat() != null && o.getLng() != null)
                .filter(o -> o.getTimeWindowStart() == null
                        || (!o.getTimeWindowStart().isBefore(from) && o.getTimeWindowStart().isBefore(to)))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /** Phase 1: urgent + heavy orders first, each to the nearest depot with room left. */
    static Map<Vehicle, List<Order>> clusterByNearestDepot(List<Vehicle> vehicles, List<Order> orders) {
        Map<Vehicle, List<Order>> clusters = new LinkedHashMap<>();
        Map<Vehicle, Double> remaining = new LinkedHashMap<>();
        for (Vehicle v : vehicles) {
            clusters.put(v, new ArrayList<>());
            remaining.put(v, v.getCapacity());
        }

        List<Order> ordered = new ArrayList<>(orders);
        ordered.sort(Comparator.comparingInt(Order::getPriority).reversed()
                .thenComparing(Comparator.comparingDouble(Order::getLoad).reversed()));

        for (Order order : ordered) {
            Vehicle nearest = null;
            double nearestKm = Double.MAX_VALUE;
            for (Vehicle v : vehicles) {
                if (remaining.get(v) < order.getLoad()) {
                    continue;
                }
                double km = GeoUtils.haversineKm(v.getStartDepotLat(), v.getStartDepotLng(), order.getLat(), order.getLng());
                if (km < nearestKm) {
                    nearestKm = km;
                    nearest = v;
                }
            }
            if (nearest != null) {
                clusters.get(nearest).add(order);
                remaining.merge(nearest, -order.getLoad(), Double::sum);
            }
        }
        return clusters;
    }

    private record Candidate(Order order, RoutingEngineService.LegResult leg, Instant arrival, Instant departure, double score) {
    }

    /** Mutable per-vehicle routing state so a route can be filled in several phases. */
    private class RouteBuilder {
        private final Vehicle vehicle;
        private final Route route;
        private final Instant shiftEnd;
        private final List<RoutingEngineService.Point> polyline = new ArrayList<>();

        private RoutingEngineService.Point current;
        private Instant currentTime;
        private double remainingCapacity;
        private double totalDistanceKm;
        private double totalDurationMin;
        private int sequence = 1;

        RouteBuilder(Vehicle vehicle, java.time.LocalDate date) {
            this.vehicle = vehicle;
            this.route = Route.builder()
                    .vehicleId(vehicle.getId())
                    .vehicleLabel(vehicle.getLabel())
                    .date(date)
                    .status(RouteStatus.PLANNED)
                    .build();
            this.current = new RoutingEngineService.Point(vehicle.getStartDepotLat(), vehicle.getStartDepotLng());
            this.currentTime = clock.at(date, vehicle.getShiftStart());
            this.shiftEnd = clock.at(date, vehicle.getShiftEnd());
            this.remainingCapacity = vehicle.getCapacity();
            this.polyline.add(current);
        }

        /** Greedily appends stops drawn from {@code pool}; chosen orders are removed from {@code unassigned} too. */
        void fill(List<Order> pool, List<Order> unassigned, List<Order> assignedThisRun) {
            List<Order> available = new ArrayList<>(pool);
            available.retainAll(unassigned);

            while (true) {
                Candidate best = findBestCandidate(available);
                if (best == null) {
                    return;
                }

                Order order = best.order();
                route.getStops().add(RouteStop.builder()
                        .id(UUID.randomUUID().toString())
                        .orderId(order.getId())
                        .orderRef(order.getRef())
                        .addressText(order.getAddressText())
                        .customerName(order.getCustomerName())
                        .customerPhone(order.getCustomerPhone())
                        .notes(order.getNotes())
                        .load(order.getLoad())
                        .lat(order.getLat())
                        .lng(order.getLng())
                        .timeWindowStart(order.getTimeWindowStart())
                        .timeWindowEnd(order.getTimeWindowEnd())
                        .sequence(sequence++)
                        .plannedEta(best.arrival())
                        .status(StopStatus.PENDING)
                        .distanceFromPrevKm(best.leg().distanceKm())
                        .build());

                order.setStatus(OrderStatus.ASSIGNED);
                assignedThisRun.add(order);
                unassigned.remove(order);
                available.remove(order);

                totalDistanceKm += best.leg().distanceKm();
                totalDurationMin += best.leg().durationMin();
                polyline.addAll(best.leg().polyline());

                remainingCapacity -= order.getLoad();
                current = new RoutingEngineService.Point(order.getLat(), order.getLng());
                currentTime = best.departure();
            }
        }

        Route finish() {
            route.setPlannedDistanceKm(round2(totalDistanceKm));
            route.setPlannedDurationMin(round2(totalDurationMin));
            route.setPolyline(RoutingEngineService.encodeSimplePolyline(polyline));
            return route;
        }

        private Candidate findBestCandidate(List<Order> available) {
            Candidate best = null;

            for (Order order : available) {
                if (order.getLoad() > remainingCapacity) {
                    continue;
                }

                RoutingEngineService.Point to = new RoutingEngineService.Point(order.getLat(), order.getLng());
                RoutingEngineService.LegResult leg = routingEngineService.route(current, to);

                Instant rawArrival = currentTime.plusSeconds((long) (leg.durationMin() * 60));
                Instant arrival = rawArrival;
                if (order.getTimeWindowStart() != null && arrival.isBefore(order.getTimeWindowStart())) {
                    arrival = order.getTimeWindowStart(); // wait for the window to open
                }
                if (order.getTimeWindowEnd() != null && arrival.isAfter(order.getTimeWindowEnd())) {
                    continue; // would miss the delivery window
                }
                if (arrival.isAfter(shiftEnd)) {
                    continue; // would run past the driver's shift
                }

                double score = stopScore(leg.distanceKm(), order, rawArrival, arrival);
                if (best == null || score < best.score()) {
                    best = new Candidate(order, leg, arrival, arrival.plusSeconds(HANDLING_SECONDS), score);
                }
            }
            return best;
        }
    }

    /** Lower is better: road-km, minus priority pull, plus idle-wait penalty, minus a bonus as a window's deadline nears. */
    static double stopScore(double legKm, Order order, Instant rawArrival, Instant arrival) {
        double score = legKm - order.getPriority() * PRIORITY_BONUS_KM;
        score += java.time.Duration.between(rawArrival, arrival).toMinutes() * WAIT_PENALTY_KM_PER_MIN;
        if (order.getTimeWindowEnd() != null) {
            double slackMin = Math.max(0, java.time.Duration.between(arrival, order.getTimeWindowEnd()).toMinutes());
            if (slackMin < DEADLINE_HORIZON_MIN) {
                score -= (DEADLINE_HORIZON_MIN - slackMin) / DEADLINE_HORIZON_MIN * DEADLINE_BONUS_KM;
            }
        }
        return score;
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
