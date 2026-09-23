package com.routeflow.dto.route;

import com.routeflow.domain.Route;

import java.time.LocalDate;
import java.util.List;

public record RouteResponse(
        String id,
        String vehicleId,
        String vehicleLabel,
        LocalDate date,
        double plannedDistanceKm,
        double plannedDurationMin,
        String polyline,
        String status,
        List<RouteStopResponse> stops
) {
    public static RouteResponse from(Route r) {
        return new RouteResponse(
                r.getId(),
                r.getVehicleId(),
                r.getVehicleLabel(),
                r.getDate(),
                r.getPlannedDistanceKm(),
                r.getPlannedDurationMin(),
                r.getPolyline(),
                r.getStatus().name(),
                r.getStops().stream().map(RouteStopResponse::from).toList()
        );
    }
}
