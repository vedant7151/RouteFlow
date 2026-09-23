package com.routeflow.dto.route;

import jakarta.validation.constraints.NotNull;

/** Move a stop to a different route (vehicle), at a given sequence position. */
public record ReassignStopRequest(
        @NotNull String targetRouteId,
        Integer sequence
) {
}
