package com.routeflow.dto.order;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.Instant;

public record OrderRequest(
        @NotBlank String ref,
        @NotBlank String addressText,
        Double lat,
        Double lng,
        Instant timeWindowStart,
        Instant timeWindowEnd,
        @PositiveOrZero Double load,
        Integer priority,
        String notes,
        String customerName,
        String customerPhone
) {
}
