package com.routeflow.domain;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "proof_of_deliveries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProofOfDelivery {

    @Id
    private String id;

    @Indexed(unique = true)
    private String orderId;

    /** Relative path/URL under the configured upload dir. */
    private String photoUrl;

    /** Data-URL or raw base64 of a canvas-drawn or typed signature. */
    private String signatureData;

    private String notes;

    private Double capturedLat;
    private Double capturedLng;

    @Builder.Default
    private Instant capturedAt = Instant.now();
}
