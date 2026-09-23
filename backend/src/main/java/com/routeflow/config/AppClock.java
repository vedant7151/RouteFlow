package com.routeflow.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * Single source of truth for "which day is it" and "when does a shift start" so routes, orders,
 * analytics and seed data all agree on the operating timezone (routeflow.time-zone). Previously
 * these were hardcoded to UTC, which made "today" wrong for a dispatcher in India between
 * midnight and 05:30 local time.
 */
@Component
public class AppClock {

    private final ZoneId zone;

    public AppClock(@Value("${routeflow.time-zone:Asia/Kolkata}") String zoneId) {
        this.zone = ZoneId.of(zoneId);
    }

    public ZoneId zone() {
        return zone;
    }

    public LocalDate today() {
        return LocalDate.now(zone);
    }

    public Instant startOfDay(LocalDate date) {
        return date.atStartOfDay(zone).toInstant();
    }

    public Instant at(LocalDate date, LocalTime time) {
        return date.atTime(time).atZone(zone).toInstant();
    }

    public LocalDate dateOf(Instant instant) {
        return instant.atZone(zone).toLocalDate();
    }
}
