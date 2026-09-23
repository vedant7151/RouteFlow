package com.routeflow.dto.vehicle;

import com.routeflow.domain.Vehicle;

import java.time.LocalTime;

public record VehicleResponse(
        String id,
        String label,
        double capacity,
        double startDepotLat,
        double startDepotLng,
        LocalTime shiftStart,
        LocalTime shiftEnd,
        String vehicleType,
        String driverUserId,
        String driverName,
        boolean active
) {
    public static VehicleResponse from(Vehicle v) {
        return new VehicleResponse(
                v.getId(), v.getLabel(), v.getCapacity(), v.getStartDepotLat(), v.getStartDepotLng(),
                v.getShiftStart(), v.getShiftEnd(), v.getVehicleType(),
                v.getDriverId(), v.getDriverName(),
                v.isActive()
        );
    }
}
