package com.routeflow.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.routeflow.util.GeoUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Thin client for OSRM (Open Source Routing Machine). Falls back to a haversine-distance
 * straight line + a configurable average speed whenever the routing engine is unreachable, so
 * the app keeps working offline / without a self-hosted OSRM instance.
 */
@Service
@Slf4j
public class RoutingEngineService {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Leg results are pure functions of (from, to): the optimizer asks for the same pair many times. */
    private final Map<String, LegResult> legCache = new ConcurrentHashMap<>();
    private static final int MAX_CACHED_LEGS = 5000;

    /** After a failed OSRM call, skip the network for a while so one outage doesn't cost 8s per leg. */
    private static final long OSRM_COOLDOWN_MS = 60_000;
    private volatile long osrmUnavailableUntil = 0;

    @Value("${routeflow.routing.osrm.base-url}")
    private String baseUrl;

    @Value("${routeflow.routing.osrm.profile}")
    private String profile;

    @Value("${routeflow.routing.fallback-avg-speed-kmh}")
    private double fallbackAvgSpeedKmh;

    public record Point(double lat, double lng) {
    }

    public record LegResult(double distanceKm, double durationMin, List<Point> polyline) {
    }

    /** Distance+duration (and geometry) for a single leg from -> to. Never throws; falls back on error. */
    public LegResult route(Point from, Point to) {
        String key = String.format(Locale.ROOT, "%.5f,%.5f>%.5f,%.5f", from.lat(), from.lng(), to.lat(), to.lng());
        LegResult cached = legCache.get(key);
        if (cached != null) {
            return cached;
        }

        boolean osrmSkipped = System.currentTimeMillis() < osrmUnavailableUntil;
        LegResult result = osrmSkipped ? fallback(from, to) : fetchFromOsrm(from, to);

        // Only cache real road results; fallbacks are retried once OSRM is back.
        if (!osrmSkipped && System.currentTimeMillis() >= osrmUnavailableUntil && legCache.size() < MAX_CACHED_LEGS) {
            legCache.put(key, result);
        }
        return result;
    }

    private LegResult fetchFromOsrm(Point from, Point to) {
        try {
            String coords = String.format(Locale.ROOT, "%f,%f;%f,%f", from.lng(), from.lat(), to.lng(), to.lat());
            URI uri = URI.create(baseUrl + "/route/v1/" + profile + "/" + coords
                    + "?overview=full&geometries=geojson");
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(8))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("OSRM route failed with status {}, falling back to haversine", response.statusCode());
                osrmUnavailableUntil = System.currentTimeMillis() + OSRM_COOLDOWN_MS;
                return fallback(from, to);
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode routes = root.path("routes");
            if (!routes.isArray() || routes.isEmpty()) {
                return fallback(from, to);
            }
            JsonNode route = routes.get(0);
            double distanceKm = route.path("distance").asDouble() / 1000.0;
            double durationMin = route.path("duration").asDouble() / 60.0;

            List<Point> polyline = List.of();
            JsonNode coordinates = route.path("geometry").path("coordinates");
            if (coordinates.isArray()) {
                polyline = new java.util.ArrayList<>();
                for (JsonNode c : coordinates) {
                    polyline.add(new Point(c.get(1).asDouble(), c.get(0).asDouble()));
                }
            }

            return new LegResult(distanceKm, durationMin, polyline);
        } catch (Exception ex) {
            log.warn("OSRM call failed, falling back to haversine for the next {}s: {}",
                    OSRM_COOLDOWN_MS / 1000, ex.getMessage());
            osrmUnavailableUntil = System.currentTimeMillis() + OSRM_COOLDOWN_MS;
            return fallback(from, to);
        }
    }

    private LegResult fallback(Point from, Point to) {
        double distanceKm = GeoUtils.haversineKm(from.lat(), from.lng(), to.lat(), to.lng());
        double durationMin = (distanceKm / fallbackAvgSpeedKmh) * 60.0;
        return new LegResult(distanceKm, durationMin, List.of(from, to));
    }

    /** Encode a sequence of points as a simple "lat,lng;lat,lng;..." string for storage/rendering. */
    public static String encodeSimplePolyline(List<Point> points) {
        return points.stream()
                .map(p -> String.format(Locale.ROOT, "%.6f,%.6f", p.lat(), p.lng()))
                .collect(Collectors.joining(";"));
    }
}
