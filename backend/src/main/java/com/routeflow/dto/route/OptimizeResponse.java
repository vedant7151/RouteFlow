package com.routeflow.dto.route;

import java.util.List;

public record OptimizeResponse(
        List<RouteResponse> routes,
        List<String> unassignedOrderIds
) {
}
