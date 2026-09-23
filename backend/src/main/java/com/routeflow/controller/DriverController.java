package com.routeflow.controller;

import com.routeflow.domain.Route;
import com.routeflow.domain.enums.RouteStatus;
import com.routeflow.dto.route.RouteResponse;
import com.routeflow.exception.ResourceNotFoundException;
import com.routeflow.repository.RouteRepository;
import com.routeflow.security.RouteFlowUserDetails;
import com.routeflow.service.DriverAccessService;
import com.routeflow.service.StopService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/** What the driver web view needs: "my vehicle, my dispatched routes today, start route". */
@RestController
@RequestMapping("/api/driver")
@RequiredArgsConstructor
public class DriverController {

    private final DriverAccessService driverAccess;
    private final RouteRepository routeRepository;
    private final StopService stopService;

    public record DriverRoutesResponse(String vehicleId, String vehicleLabel, List<RouteResponse> routes) {
    }

    @GetMapping("/routes")
    public DriverRoutesResponse myRoutes(
            @AuthenticationPrincipal RouteFlowUserDetails user,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return driverAccess.vehicleOf(user)
                .map(vehicle -> new DriverRoutesResponse(
                        vehicle.getId(),
                        vehicle.getLabel(),
                        // PLANNED routes have not been pushed to the driver yet.
                        routeRepository.findByVehicleId(vehicle.getId()).stream()
                                .filter(r -> date.equals(r.getDate()))
                                .filter(r -> r.getStatus() != RouteStatus.PLANNED && r.getStatus() != RouteStatus.CANCELLED)
                                .map(RouteResponse::from)
                                .toList()))
                .orElseGet(() -> new DriverRoutesResponse(null, null, List.of()));
    }

    @PostMapping("/routes/{id}/start")
    public RouteResponse start(@AuthenticationPrincipal RouteFlowUserDetails user, @PathVariable String id) {
        Route route = routeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Route not found: " + id));
        driverAccess.assertOwnsRoute(user, route);
        return RouteResponse.from(stopService.startRoute(id));
    }
}
