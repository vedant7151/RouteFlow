package com.routeflow.domain;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.LocalTime;

@Document(collection = "vehicles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Vehicle {

    @Id
    private String id;

    private String label;

    /** Max total order "load" (arbitrary units - weight/volume/parcel count) this vehicle can carry. */
    private double capacity;

    private double startDepotLat;
    private double startDepotLng;

    private LocalTime shiftStart;
    private LocalTime shiftEnd;

    /** Free-text vehicle type / skills, e.g. "van", "bike", "refrigerated". */
    private String vehicleType;

    /**
     * Denormalized driver reference: id + a display-name snapshot, kept in sync by
     * VehicleService whenever the driver assignment changes. Avoids a User lookup on every
     * vehicle read (Mongo has no cross-document joins/lazy loading like JPA did).
     */
    private String driverId;
    private String driverName;

    @Builder.Default
    private boolean active = true;

    @Builder.Default
    private Instant createdAt = Instant.now();
}
