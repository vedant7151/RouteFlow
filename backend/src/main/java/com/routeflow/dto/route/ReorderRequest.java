package com.routeflow.dto.route;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** New stop ordering for a single route, expressed as an ordered list of RouteStop ids. */
public record ReorderRequest(
        @NotEmpty List<String> orderedStopIds
) {
}
