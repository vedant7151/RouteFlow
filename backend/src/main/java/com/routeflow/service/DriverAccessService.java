package com.routeflow.service;

import com.routeflow.domain.Route;
import com.routeflow.domain.Vehicle;
import com.routeflow.exception.ForbiddenException;
import com.routeflow.exception.ResourceNotFoundException;
import com.routeflow.repository.RouteRepository;
import com.routeflow.repository.VehicleRepository;
import com.routeflow.security.RouteFlowUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Role-based endpoint protection stops at the role; a DRIVER additionally may only touch their own
 * vehicle, its stops and its orders. Dispatchers/managers are unrestricted, so every check here is
 * a no-op for them.
 */
@Service
@RequiredArgsConstructor
public class DriverAccessService {

    private final VehicleRepository vehicleRepository;
    private final RouteRepository routeRepository;

    public static boolean isDriver(RouteFlowUserDetails user) {
        return user != null && "DRIVER".equals(user.getRole());
    }

    public Optional<Vehicle> vehicleOf(RouteFlowUserDetails user) {
        return vehicleRepository.findByDriverId(user.getId());
    }

    public void assertOwnsVehicle(RouteFlowUserDetails user, String vehicleId) {
        if (!isDriver(user)) {
            return;
        }
        boolean owns = vehicleOf(user).map(v -> v.getId().equals(vehicleId)).orElse(false);
        if (!owns) {
            throw new ForbiddenException("That vehicle is not assigned to you");
        }
    }

    public void assertOwnsRoute(RouteFlowUserDetails user, Route route) {
        assertOwnsVehicle(user, route.getVehicleId());
    }

    public void assertOwnsStop(RouteFlowUserDetails user, String stopId) {
        if (!isDriver(user)) {
            return;
        }
        Route route = routeRepository.findByStopsId(stopId)
                .orElseThrow(() -> new ResourceNotFoundException("Stop not found: " + stopId));
        assertOwnsRoute(user, route);
    }

    public void assertOwnsOrder(RouteFlowUserDetails user, String orderId) {
        if (!isDriver(user)) {
            return;
        }
        String vehicleId = vehicleOf(user).map(Vehicle::getId).orElse(null);
        boolean owns = vehicleId != null && routeRepository.findByStopsOrderId(orderId).stream()
                .anyMatch(r -> vehicleId.equals(r.getVehicleId()));
        if (!owns) {
            throw new ForbiddenException("That order is not on one of your routes");
        }
    }
}
