package com.routeflow.dto.route;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record OptimizeRequest(
        @NotNull LocalDate date,
        List<String> vehicleIds
) {
}
