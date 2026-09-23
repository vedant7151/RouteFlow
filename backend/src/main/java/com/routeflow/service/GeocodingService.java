package com.routeflow.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

/**
 * Free geocoding via Nominatim (OpenStreetMap). Respect the usage policy: identify with a real
 * User-Agent and keep request volume low (self-host Nominatim/Photon for production volume).
 */
@Service
@Slf4j
public class GeocodingService {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${routeflow.geocoding.nominatim.base-url}")
    private String baseUrl;

    @Value("${routeflow.geocoding.nominatim.user-agent}")
    private String userAgent;

    public record GeocodeResult(double lat, double lng, String displayName) {
    }

    public Optional<GeocodeResult> geocode(String addressText) {
        try {
            String query = URLEncoder.encode(addressText, StandardCharsets.UTF_8);
            URI uri = URI.create(baseUrl + "/search?q=" + query + "&format=json&limit=1");
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .header("User-Agent", userAgent)
                    .timeout(Duration.ofSeconds(8))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Nominatim geocode failed with status {}", response.statusCode());
                return Optional.empty();
            }

            JsonNode results = objectMapper.readTree(response.body());
            if (!results.isArray() || results.isEmpty()) {
                return Optional.empty();
            }
            JsonNode first = results.get(0);
            double lat = Double.parseDouble(first.get("lat").asText());
            double lng = Double.parseDouble(first.get("lon").asText());
            String displayName = first.path("display_name").asText(addressText);
            return Optional.of(new GeocodeResult(lat, lng, displayName));
        } catch (Exception ex) {
            log.warn("Geocoding failed for '{}': {}", addressText, ex.getMessage());
            return Optional.empty();
        }
    }
}
