package com.routeflow.controller;

import com.routeflow.dto.route.ReassignStopRequest;
import com.routeflow.dto.route.RouteStopResponse;
import com.routeflow.dto.stop.StopStatusUpdateRequest;
import com.routeflow.security.RouteFlowUserDetails;
import com.routeflow.service.DispatchService;
import com.routeflow.service.DriverAccessService;
import com.routeflow.service.StopService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/stops")
@RequiredArgsConstructor
public class StopController {

    private final StopService stopService;
    private final DispatchService dispatchService;
    private final DriverAccessService driverAccess;

    @PatchMapping("/{id}/status")
    public RouteStopResponse updateStatus(
            @AuthenticationPrincipal RouteFlowUserDetails user,
            @PathVariable String id,
            @Valid @RequestBody StopStatusUpdateRequest request
    ) {
        driverAccess.assertOwnsStop(user, id);
        return RouteStopResponse.from(stopService.updateStatus(id, request));
    }

    @PatchMapping("/{id}/reassign")
    public void reassign(@PathVariable String id, @Valid @RequestBody ReassignStopRequest request) {
        dispatchService.reassignStop(id, request);
    }
}
