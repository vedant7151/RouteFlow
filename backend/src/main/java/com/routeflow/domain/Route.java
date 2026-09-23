package com.routeflow.domain;

import com.routeflow.domain.enums.RouteStatus;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "routes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Route {

    @Id
    private String id;

    /** Denormalized alongside vehicleId - see RouteStop's note on why (no Mongo joins). */
    private String vehicleId;
    private String vehicleLabel;

    private LocalDate date;

    private double plannedDistanceKm;
    private double plannedDurationMin;

    /** Encoded polyline (or simple "lat,lng;lat,lng;..." fallback) for map rendering. */
    private String polyline;

    @Builder.Default
    private RouteStatus status = RouteStatus.PLANNED;

    /** Embedded, ordered by sequence - kept sorted by the services that mutate it. */
    @Builder.Default
    private List<RouteStop> stops = new ArrayList<>();

    @Builder.Default
    private Instant createdAt = Instant.now();

    private Instant dispatchedAt;
}
