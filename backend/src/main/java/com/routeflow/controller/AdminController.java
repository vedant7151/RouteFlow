package com.routeflow.controller;

import com.routeflow.exception.ForbiddenException;
import com.routeflow.service.DemoDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Dev/demo tooling. Disabled (403) when routeflow.seed.enabled=false so it can never wipe a real database. */
@RestController
@RequestMapping("/api/admin/demo")
@RequiredArgsConstructor
public class AdminController {

    private final DemoDataService demoDataService;

    @Value("${routeflow.seed.enabled}")
    private boolean seedEnabled;

    /** Wipes orders/routes/PODs/pings/vehicles and re-seeds the demo scenario. Users are kept. */
    @PostMapping("/reset")
    public DemoDataService.Summary reset() {
        if (!seedEnabled) {
            throw new ForbiddenException("Demo data tools are disabled (routeflow.seed.enabled=false)");
        }
        return demoDataService.resetAndSeed();
    }
}
