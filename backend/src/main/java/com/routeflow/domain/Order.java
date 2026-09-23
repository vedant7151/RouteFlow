package com.routeflow.domain;

import com.routeflow.domain.enums.OrderStatus;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    private String id;

    /** Human-friendly reference, e.g. order/invoice number. */
    private String ref;

    private String addressText;

    private Double lat;
    private Double lng;

    private Instant timeWindowStart;
    private Instant timeWindowEnd;

    /** Weight/volume/parcel-count - consumed against a vehicle's capacity. */
    @Builder.Default
    private double load = 1.0;

    /** Higher number = higher priority when the optimizer must choose. */
    @Builder.Default
    private int priority = 0;

    private String notes;

    @Builder.Default
    private OrderStatus status = OrderStatus.PENDING;

    private String exceptionReason;

    private String customerName;
    private String customerPhone;

    @Builder.Default
    private Instant createdAt = Instant.now();

    @Builder.Default
    private Instant updatedAt = Instant.now();
}
