package com.routeflow.service;

import com.routeflow.config.AppClock;
import com.routeflow.domain.LocationPing;
import com.routeflow.domain.Order;
import com.routeflow.domain.Route;
import com.routeflow.domain.RouteStop;
import com.routeflow.domain.Vehicle;
import com.routeflow.domain.enums.OrderStatus;
import com.routeflow.domain.enums.StopStatus;
import com.routeflow.dto.analytics.AnalyticsSummaryResponse;
import com.routeflow.dto.analytics.AnalyticsSummaryResponse.DailyCount;
import com.routeflow.dto.analytics.AnalyticsSummaryResponse.DailyDistance;
import com.routeflow.dto.analytics.AnalyticsSummaryResponse.DailyOnTime;
import com.routeflow.dto.analytics.AnalyticsSummaryResponse.DriverLeaderboardEntry;
import com.routeflow.dto.analytics.AnalyticsSummaryResponse.FailureReason;
import com.routeflow.repository.LocationPingRepository;
import com.routeflow.repository.OrderRepository;
import com.routeflow.repository.RouteRepository;
import com.routeflow.repository.VehicleRepository;
import com.routeflow.util.GeoUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Route stops are the source of truth for delivery performance: a stop knows its planned ETA, when
 * the driver actually arrived and its final status.
 * <ul>
 *   <li><b>On-time</b>: delivered stop whose actual arrival is within {@value #ON_TIME_GRACE_MINUTES} minutes of its planned ETA.</li>
 *   <li><b>Avg delivery time</b>: minutes from the route being dispatched (or created) to a delivered stop's arrival.</li>
 *   <li><b>Actual km</b>: summed GPS-ping distance for a vehicle on a route's day - counted once per vehicle-day.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    static final long ON_TIME_GRACE_MINUTES = 10;

    private final OrderRepository orderRepository;
    private final RouteRepository routeRepository;
    private final LocationPingRepository locationPingRepository;
    private final VehicleRepository vehicleRepository;
    private final AppClock clock;

    public AnalyticsSummaryResponse summary(LocalDate from, LocalDate to) {
        List<Route> routes = routeRepository.findInDateRange(from, to);

        long delivered = 0;
        long failed = 0;
        long onTime = 0;
        double totalDeliveryMinutes = 0;

        Map<LocalDate, long[]> perDay = new TreeMap<>(); // [delivered, onTime]

        for (Route route : routes) {
            Instant startedAt = route.getDispatchedAt() != null ? route.getDispatchedAt() : route.getCreatedAt();
            for (RouteStop stop : route.getStops()) {
                if (stop.getStatus() == StopStatus.FAILED) {
                    failed++;
                }
                if (stop.getStatus() != StopStatus.DELIVERED) {
                    continue;
                }
                delivered++;
                boolean stopOnTime = isOnTime(stop);
                if (stopOnTime) {
                    onTime++;
                }
                if (stop.getActualArrival() != null && startedAt != null) {
                    totalDeliveryMinutes += Duration.between(startedAt, stop.getActualArrival()).toMinutes();
                }
                long[] counts = perDay.computeIfAbsent(route.getDate(), d -> new long[2]);
                counts[0]++;
                counts[1] += stopOnTime ? 1 : 0;
            }
        }

        List<DailyCount> deliveriesPerDay = new ArrayList<>();
        List<DailyOnTime> onTimeTrend = new ArrayList<>();
        perDay.forEach((day, counts) -> {
            deliveriesPerDay.add(new DailyCount(day.toString(), counts[0]));
            onTimeTrend.add(new DailyOnTime(day.toString(), pct(counts[1], counts[0]), counts[0]));
        });

        List<DailyDistance> distancePerDay = buildDistancePerDay(routes);
        double totalPlannedKm = distancePerDay.stream().mapToDouble(DailyDistance::plannedKm).sum();
        double totalActualKm = distancePerDay.stream().mapToDouble(DailyDistance::actualKm).sum();

        Map<String, Long> ordersByStatus = orderRepository.findAll().stream()
                .collect(Collectors.groupingBy(o -> o.getStatus().name(), Collectors.counting()));

        return new AnalyticsSummaryResponse(
                delivered, failed, pct(onTime, delivered),
                delivered == 0 ? 0.0 : round2(totalDeliveryMinutes / delivered),
                round2(totalPlannedKm), round2(totalActualKm),
                deliveriesPerDay, onTimeTrend, distancePerDay,
                buildLeaderboard(routes), buildFailureReasons(routes), ordersByStatus
        );
    }

    static boolean isOnTime(RouteStop stop) {
        if (stop.getActualArrival() == null || stop.getPlannedEta() == null) {
            return false;
        }
        return !stop.getActualArrival().isAfter(stop.getPlannedEta().plusSeconds(ON_TIME_GRACE_MINUTES * 60));
    }

    private List<DailyDistance> buildDistancePerDay(List<Route> routes) {
        Map<LocalDate, double[]> byDay = new TreeMap<>(); // [planned, actual]
        Set<String> countedVehicleDays = new HashSet<>();

        for (Route route : routes) {
            double[] km = byDay.computeIfAbsent(route.getDate(), d -> new double[2]);
            km[0] += route.getPlannedDistanceKm();

            if (countedVehicleDays.add(route.getVehicleId() + "@" + route.getDate())) {
                km[1] += actualKm(route.getVehicleId(), route.getDate());
            }
        }

        List<DailyDistance> result = new ArrayList<>();
        byDay.forEach((day, km) -> result.add(new DailyDistance(day.toString(), round2(km[0]), round2(km[1]))));
        return result;
    }

    private double actualKm(String vehicleId, LocalDate date) {
        List<LocationPing> pings = locationPingRepository.findByVehicleIdAndTimestampBetweenOrderByTimestampAsc(
                vehicleId, clock.startOfDay(date), clock.startOfDay(date.plusDays(1)));
        double total = 0;
        for (int i = 1; i < pings.size(); i++) {
            LocationPing a = pings.get(i - 1);
            LocationPing b = pings.get(i);
            total += GeoUtils.haversineKm(a.getLat(), a.getLng(), b.getLat(), b.getLng());
        }
        return total;
    }

    private List<DriverLeaderboardEntry> buildLeaderboard(List<Route> routes) {
        // Route only carries a denormalized vehicleLabel snapshot (no live join in Mongo), so
        // driver name is resolved with one batch fetch of the vehicles actually referenced here.
        List<String> vehicleIds = routes.stream().map(Route::getVehicleId).distinct().toList();
        Map<String, Vehicle> vehiclesById = vehicleRepository.findAllById(vehicleIds).stream()
                .collect(Collectors.toMap(Vehicle::getId, v -> v));

        Map<String, DriverLeaderboardEntry> byVehicle = new LinkedHashMap<>();
        for (Route route : routes) {
            long delivered = route.getStops().stream().filter(s -> s.getStatus() == StopStatus.DELIVERED).count();
            Vehicle vehicle = vehiclesById.get(route.getVehicleId());
            byVehicle.merge(
                    route.getVehicleId(),
                    new DriverLeaderboardEntry(
                            route.getVehicleId(), route.getVehicleLabel(),
                            vehicle != null ? vehicle.getDriverName() : null,
                            delivered, route.getPlannedDistanceKm()
                    ),
                    (existing, incoming) -> new DriverLeaderboardEntry(
                            existing.vehicleId(), existing.label(), existing.driverName(),
                            existing.delivered() + incoming.delivered(),
                            round2(existing.km() + incoming.km())
                    )
            );
        }
        return byVehicle.values().stream()
                .sorted(Comparator.comparingLong(DriverLeaderboardEntry::delivered).reversed())
                .toList();
    }

    private List<FailureReason> buildFailureReasons(List<Route> routes) {
        Set<String> failedOrderIds = routes.stream()
                .flatMap(r -> r.getStops().stream())
                .filter(s -> s.getStatus() == StopStatus.FAILED)
                .map(RouteStop::getOrderId)
                .collect(Collectors.toSet());
        if (failedOrderIds.isEmpty()) {
            return List.of();
        }
        return orderRepository.findAllById(failedOrderIds).stream()
                .filter(o -> o.getStatus() == OrderStatus.FAILED)
                .map(Order::getExceptionReason)
                .map(reason -> reason == null || reason.isBlank() ? "Unspecified" : reason)
                .collect(Collectors.groupingBy(r -> r, Collectors.counting()))
                .entrySet().stream()
                .map(e -> new FailureReason(e.getKey(), e.getValue()))
                .sorted(Comparator.comparingLong(FailureReason::count).reversed())
                .toList();
    }

    private double pct(long part, long total) {
        return total == 0 ? 0.0 : round2(100.0 * part / total);
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
