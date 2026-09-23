package com.routeflow.service;

import com.routeflow.config.AppClock;
import com.routeflow.domain.Order;
import com.routeflow.domain.Route;
import com.routeflow.domain.Vehicle;
import com.routeflow.domain.enums.OrderStatus;
import com.routeflow.dto.route.OptimizeRequest;
import com.routeflow.dto.route.OptimizeResponse;
import com.routeflow.repository.OrderRepository;
import com.routeflow.repository.RouteRepository;
import com.routeflow.repository.VehicleRepository;
import com.routeflow.util.GeoUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RouteOptimizationServiceTest {

    private static final LocalDate DATE = LocalDate.of(2026, 9, 21);

    @Mock OrderRepository orderRepository;
    @Mock VehicleRepository vehicleRepository;
    @Mock RouteRepository routeRepository;
    @Mock RoutingEngineService routing;

    private final AppClock clock = new AppClock("Asia/Kolkata");
    private RouteOptimizationService service;

    @BeforeEach
    void setUp() {
        // Deterministic stand-in for OSRM: road km = 1.3 x straight line, 30 km/h.
        when(routing.route(any(), any())).thenAnswer(inv -> {
            RoutingEngineService.Point a = inv.getArgument(0);
            RoutingEngineService.Point b = inv.getArgument(1);
            double km = GeoUtils.haversineKm(a.lat(), a.lng(), b.lat(), b.lng()) * 1.3;
            return new RoutingEngineService.LegResult(km, km / 30 * 60, List.of(a, b));
        });
        when(routeRepository.save(any(Route.class))).thenAnswer(inv -> inv.getArgument(0));
        service = new RouteOptimizationService(orderRepository, vehicleRepository, routeRepository, routing, clock);
    }

    private Vehicle vehicle(String id, double capacity, double lat, double lng) {
        return Vehicle.builder().id(id).label(id).capacity(capacity)
                .startDepotLat(lat).startDepotLng(lng)
                .shiftStart(LocalTime.of(9, 0)).shiftEnd(LocalTime.of(18, 0)).active(true).build();
    }

    private Order order(String id, double lat, double lng, double load, int priority) {
        return Order.builder().id(id).ref(id).addressText(id).lat(lat).lng(lng).load(load).priority(priority)
                .status(OrderStatus.PENDING).build();
    }

    private OptimizeResponse optimize(List<Vehicle> vehicles, List<Order> orders) {
        when(vehicleRepository.findByActiveTrue()).thenReturn(vehicles);
        when(orderRepository.findByStatus(OrderStatus.PENDING)).thenReturn(orders);
        return service.optimize(new OptimizeRequest(DATE, null));
    }

    @Test
    void neverExceedsVehicleCapacity_andReportsTheOverflowAsUnassigned() {
        OptimizeResponse response = optimize(
                List.of(vehicle("van", 5, 19.0, 72.8)),
                List.of(order("a", 19.01, 72.8, 3, 0), order("b", 19.02, 72.8, 3, 0)));

        assertEquals(1, response.routes().size());
        assertEquals(1, response.routes().get(0).stops().size());
        assertEquals(1, response.unassignedOrderIds().size());
    }

    @Test
    void orderHeavierThanEveryVehicleStaysUnassigned() {
        OptimizeResponse response = optimize(
                List.of(vehicle("van", 10, 19.0, 72.8)),
                List.of(order("huge", 19.01, 72.8, 90, 1)));

        assertTrue(response.routes().isEmpty());
        assertEquals(List.of("huge"), response.unassignedOrderIds());
    }

    @Test
    void rejectsAStopThatWouldArriveAfterItsTimeWindowCloses() {
        Instant shiftStart = clock.at(DATE, LocalTime.of(9, 0));
        Order tooLate = order("late", 19.2, 72.85, 1, 0); // ~25 km away: cannot arrive by 09:05
        tooLate.setTimeWindowStart(shiftStart);
        tooLate.setTimeWindowEnd(shiftStart.plusSeconds(5 * 60));

        OptimizeResponse response = optimize(List.of(vehicle("van", 10, 19.0, 72.8)), List.of(tooLate));

        assertEquals(List.of("late"), response.unassignedOrderIds());
    }

    @Test
    void waitsForATimeWindowToOpenInsteadOfArrivingEarly() {
        Instant windowOpens = clock.at(DATE, LocalTime.of(11, 0));
        Order early = order("windowed", 19.01, 72.8, 1, 0);
        early.setTimeWindowStart(windowOpens);
        early.setTimeWindowEnd(windowOpens.plusSeconds(3600));

        OptimizeResponse response = optimize(List.of(vehicle("van", 10, 19.0, 72.8)), List.of(early));

        assertEquals(windowOpens, response.routes().get(0).stops().get(0).plannedEta());
    }

    @Test
    void stopsThatWouldRunPastTheShiftEndAreNotScheduled() {
        Vehicle shortShift = vehicle("van", 10, 19.0, 72.8);
        shortShift.setShiftEnd(LocalTime.of(9, 10));

        OptimizeResponse response = optimize(List.of(shortShift), List.of(order("far", 19.4, 72.8, 1, 0)));

        assertEquals(List.of("far"), response.unassignedOrderIds());
    }

    @Test
    void anUrgentOrderIsPulledForwardEvenWhenAnotherIsCloser() {
        Order near = order("near", 19.001, 72.8, 1, 0);   // ~0.15 km
        Order urgent = order("urgent", 19.03, 72.8, 1, 2); // ~4.3 km but priority 2 (worth 8 km)

        OptimizeResponse response = optimize(List.of(vehicle("van", 10, 19.0, 72.8)), List.of(near, urgent));

        assertEquals("urgent", response.routes().get(0).stops().get(0).orderRef());
    }

    @Test
    void doesNotIdleAtAWindowThatOpensLaterWhenThereIsUsefulWorkToDoFirst() {
        Instant opensAtNoon = clock.at(DATE, LocalTime.of(12, 0));
        Order waits = order("waits", 19.005, 72.8, 1, 0);   // right next door, but not deliverable until 12:00
        waits.setTimeWindowStart(opensAtNoon);
        waits.setTimeWindowEnd(opensAtNoon.plusSeconds(3600));
        Order readyNow = order("readyNow", 19.04, 72.8, 1, 0); // 4-5 km away, deliverable immediately

        OptimizeResponse response = optimize(List.of(vehicle("van", 10, 19.0, 72.8)), List.of(waits, readyNow));

        assertEquals("readyNow", response.routes().get(0).stops().get(0).orderRef());
        assertEquals(2, response.routes().get(0).stops().size());
    }

    @Test
    void aWindowThatIsAboutToCloseIsPulledAheadOfCloserFlexibleStops() {
        Instant shiftStart = clock.at(DATE, LocalTime.of(9, 0));
        Order deadline = order("deadline", 19.055, 72.8, 1, 0); // ~6 km, must be there by 09:40
        deadline.setTimeWindowStart(shiftStart);
        deadline.setTimeWindowEnd(shiftStart.plusSeconds(40 * 60));
        Order flexible = order("flexible", 19.025, 72.8, 1, 0); // ~3 km, any time

        OptimizeResponse response = optimize(List.of(vehicle("van", 10, 19.0, 72.8)), List.of(flexible, deadline));

        assertEquals("deadline", response.routes().get(0).stops().get(0).orderRef());
        assertTrue(response.unassignedOrderIds().isEmpty());
    }

    @Test
    void ordersAreClusteredToTheNearestDepot() {
        Vehicle south = vehicle("south", 10, 18.95, 72.83);
        Vehicle north = vehicle("north", 10, 19.20, 72.85);
        Order nearSouth = order("s", 18.96, 72.83, 1, 0);
        Order nearNorth = order("n", 19.19, 72.85, 1, 0);

        Map<Vehicle, List<Order>> clusters =
                RouteOptimizationService.clusterByNearestDepot(List.of(south, north), List.of(nearSouth, nearNorth));

        assertEquals(List.of(nearSouth), clusters.get(south));
        assertEquals(List.of(nearNorth), clusters.get(north));
    }

    @Test
    void aFullNearestVehicleSpillsOrdersToTheNextDepot() {
        Vehicle tiny = vehicle("tiny", 1, 19.0, 72.8);
        Vehicle big = vehicle("big", 10, 19.3, 72.8);
        Order a = order("a", 19.0, 72.81, 1, 0);
        Order b = order("b", 19.0, 72.82, 1, 0);

        Map<Vehicle, List<Order>> clusters =
                RouteOptimizationService.clusterByNearestDepot(List.of(tiny, big), List.of(a, b));

        assertEquals(1, clusters.get(tiny).size());
        assertEquals(1, clusters.get(big).size());
    }
}
