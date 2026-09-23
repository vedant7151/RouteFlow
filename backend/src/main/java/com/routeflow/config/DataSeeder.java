package com.routeflow.config;

import com.routeflow.service.DemoDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * On startup (when routeflow.seed.enabled=true): make sure the demo accounts exist, and if the database
 * has no orders/routes at all, seed the full demo scenario (see {@link DemoDataService}). A database that
 * already holds data is left alone - use the Dashboard's "Reset demo data" button (or
 * POST /api/admin/demo/reset) to rebuild the scenario on demand.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final DemoDataService demoDataService;

    @Value("${routeflow.seed.enabled}")
    private boolean enabled;

    @Override
    public void run(String... args) {
        if (!enabled) {
            return;
        }

        demoDataService.ensureDemoUsers();

        if (demoDataService.hasNoOperationalData()) {
            log.info("Empty database - seeding the demo scenario");
            demoDataService.resetAndSeed();
            log.info("Seed complete. Dispatcher login: dispatcher@routeflow.dev / Dispatcher@123");
        } else {
            log.info("Existing data found - skipping demo seed (POST /api/admin/demo/reset to rebuild it)");
        }
    }
}
