package com.routeflow.domain;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "location_pings")
@CompoundIndex(name = "vehicle_time_idx", def = "{'vehicleId': 1, 'timestamp': -1}")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LocationPing {

    @Id
    private String id;

    private String vehicleId;

    private double lat;
    private double lng;

    private Double speed;

    @Builder.Default
    private Instant timestamp = Instant.now();
}
