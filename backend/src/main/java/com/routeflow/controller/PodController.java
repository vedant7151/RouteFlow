package com.routeflow.controller;

import com.routeflow.dto.pod.PodResponse;
import com.routeflow.security.RouteFlowUserDetails;
import com.routeflow.service.DriverAccessService;
import com.routeflow.service.PodService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/orders/{orderId}/pod")
@RequiredArgsConstructor
public class PodController {

    private final PodService podService;
    private final DriverAccessService driverAccess;

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<PodResponse> capture(
            @AuthenticationPrincipal RouteFlowUserDetails user,
            @PathVariable String orderId,
            @RequestParam(required = false) MultipartFile photo,
            @RequestParam(required = false) String signatureData,
            @RequestParam(required = false) String notes,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng
    ) {
        driverAccess.assertOwnsOrder(user, orderId);
        var pod = podService.capture(orderId, photo, signatureData, notes, lat, lng);
        return ResponseEntity.status(HttpStatus.CREATED).body(PodResponse.from(pod));
    }

    @GetMapping
    public PodResponse get(@AuthenticationPrincipal RouteFlowUserDetails user, @PathVariable String orderId) {
        driverAccess.assertOwnsOrder(user, orderId);
        return PodResponse.from(podService.findByOrder(orderId));
    }
}
