package com.routeflow.dto.tracking;

import java.time.Instant;
import java.util.List;

/** Outbound broadcast payload published to /topic/vehicles/{id} (and the fleet-wide /topic/vehicles). */
public record VehicleLocationEvent(
        String vehicleId,
        Double lat,
        Double lng,
        Double speed,
        Instant timestamp,
        String activeRouteId,
        String currentStopId,
        String currentStopStatus,
        /** true when the position comes from the server-side driver simulator, false for a real device. */
        boolean simulated,
        /** Rolling ETA for every not-yet-finished stop, recomputed from this position on every ping. */
        List<StopEta> etas
) {
    public record StopEta(String stopId, Instant eta) {
    }
}
