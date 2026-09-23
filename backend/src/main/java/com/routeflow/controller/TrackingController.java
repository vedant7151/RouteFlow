package com.routeflow.controller;

import com.routeflow.dto.tracking.LocationPingMessage;
import com.routeflow.security.RouteFlowUserDetails;
import com.routeflow.service.DriverAccessService;
import com.routeflow.service.TrackingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Optional;

@RestController
@RequiredArgsConstructor
public class TrackingController {

    private final TrackingService trackingService;
    private final DriverAccessService driverAccess;

    /** STOMP: driver web view / simulator publish to /app/location. */
    @MessageMapping("/location")
    public void onLocation(LocationPingMessage message, Principal principal) {
        // The STOMP CONNECT frame was authenticated by StompAuthInterceptor; a driver may only report their own vehicle.
        if (principal instanceof UsernamePasswordAuthenticationToken auth
                && auth.getPrincipal() instanceof RouteFlowUserDetails user) {
            driverAccess.assertOwnsVehicle(user, message.vehicleId());
        }
        trackingService.ingest(message);
    }

    /** REST fallback for clients that can't hold a WebSocket open (or for quick testing/curl). */
    @PostMapping("/api/tracking/location")
    public void postLocation(@AuthenticationPrincipal RouteFlowUserDetails user, @Valid @RequestBody LocationPingMessage message) {
        driverAccess.assertOwnsVehicle(user, message.vehicleId());
        trackingService.ingest(message);
    }

    public record LastLocationResponse(double lat, double lng, Double speed, java.time.Instant timestamp) {
    }

    @GetMapping("/api/tracking/vehicles/{vehicleId}/last")
    public Optional<LastLocationResponse> last(@PathVariable String vehicleId) {
        return trackingService.latestFor(vehicleId)
                .map(p -> new LastLocationResponse(p.getLat(), p.getLng(), p.getSpeed(), p.getTimestamp()));
    }
}
