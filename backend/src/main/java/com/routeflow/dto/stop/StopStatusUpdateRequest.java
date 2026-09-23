package com.routeflow.dto.stop;

import com.routeflow.domain.enums.StopStatus;
import jakarta.validation.constraints.NotNull;

public record StopStatusUpdateRequest(
        @NotNull StopStatus status,
        String exceptionReason,
        Double lat,
        Double lng
) {
}
