package com.routeflow.dto.tracking;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/** Inbound message a driver's web view sends over STOMP (/app/location) or REST fallback. */
public record LocationPingMessage(
        @NotNull String vehicleId,
        @NotNull Double lat,
        @NotNull Double lng,
        Double speed,
        Instant timestamp
) {
}
