package com.routeflow.service;

import com.routeflow.domain.RouteStop;
import com.routeflow.dto.tracking.VehicleLocationEvent;
import com.routeflow.util.GeoUtils;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrackingAndAnalyticsRulesTest {

    private RouteStop stop(String id, double lat, double lng) {
        return RouteStop.builder().id(id).lat(lat).lng(lng).build();
    }

    private TrackingService tracking(double speedKmh, double simulatorMultiplier) {
        TrackingService service = new TrackingService(null, null, null, null);
        ReflectionTestUtils.setField(service, "avgSpeedKmh", speedKmh);
        ReflectionTestUtils.setField(service, "simulatorSpeedMultiplier", simulatorMultiplier);
        return service;
    }

    @Test
    void rollingEtasAreMonotonicAndGetLaterForFurtherStops() {
        List<VehicleLocationEvent.StopEta> etas = tracking(30, 1).rollingEtas(19.0, 72.8,
                List.of(stop("a", 19.05, 72.8), stop("b", 19.10, 72.8)), false);

        assertEquals(2, etas.size());
        assertTrue(etas.get(1).eta().isAfter(etas.get(0).eta()));
        assertTrue(etas.get(0).eta().isAfter(Instant.now()));
    }

    @Test
    void theSimulatorsFastForwardedEtasAreSoonerThanRealTimeOnes() {
        List<RouteStop> stops = List.of(stop("a", 19.10, 72.8));

        Instant real = tracking(30, 8).rollingEtas(19.0, 72.8, stops, false).get(0).eta();
        Instant simulated = tracking(30, 8).rollingEtas(19.0, 72.8, stops, true).get(0).eta();

        assertTrue(simulated.isBefore(real));
    }

    @Test
    void anEtaNeverPrecedesTheStopsTimeWindowOpening() {
        Instant opens = Instant.now().plusSeconds(3 * 3600);
        RouteStop windowed = stop("w", 19.001, 72.8);
        windowed.setTimeWindowStart(opens);

        assertEquals(opens, tracking(30, 1).rollingEtas(19.0, 72.8, List.of(windowed), false).get(0).eta());
    }

    @Test
    void onTimeMeansArrivingWithinTheGraceOfThePlannedEta() {
        Instant eta = Instant.parse("2026-09-21T04:00:00Z");
        RouteStop onTime = RouteStop.builder().plannedEta(eta).actualArrival(eta.plusSeconds(9 * 60)).build();
        RouteStop late = RouteStop.builder().plannedEta(eta).actualArrival(eta.plusSeconds(11 * 60)).build();
        RouteStop early = RouteStop.builder().plannedEta(eta).actualArrival(eta.minusSeconds(20 * 60)).build();
        RouteStop unknown = RouteStop.builder().plannedEta(eta).build();

        assertTrue(AnalyticsService.isOnTime(onTime));
        assertFalse(AnalyticsService.isOnTime(late));
        assertTrue(AnalyticsService.isOnTime(early));
        assertFalse(AnalyticsService.isOnTime(unknown));
    }

    @Test
    void haversineMatchesAKnownDistance() {
        // Gateway of India -> Bandra Fort is roughly 17 km as the crow flies.
        double km = GeoUtils.haversineKm(18.9220, 72.8347, 19.0421, 72.8189);
        assertTrue(km > 12 && km < 15, "got " + km);
        assertEquals(0.0, GeoUtils.haversineKm(19.0, 72.8, 19.0, 72.8), 1e-9);
    }
}
