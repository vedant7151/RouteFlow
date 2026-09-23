package com.routeflow.dto.vehicle;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalTime;

public record VehicleRequest(
        @NotBlank String label,
        @Positive double capacity,
        @NotNull Double startDepotLat,
        @NotNull Double startDepotLng,
        @NotNull LocalTime shiftStart,
        @NotNull LocalTime shiftEnd,
        String vehicleType,
        String driverUserId
) {
}
