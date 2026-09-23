package com.routeflow.dto.order;

import com.routeflow.domain.Order;

import java.time.Instant;

public record OrderResponse(
        String id,
        String ref,
        String addressText,
        Double lat,
        Double lng,
        Instant timeWindowStart,
        Instant timeWindowEnd,
        double load,
        int priority,
        String notes,
        String status,
        String exceptionReason,
        String customerName,
        String customerPhone,
        Instant createdAt,
        Instant updatedAt
) {
    public static OrderResponse from(Order o) {
        return new OrderResponse(
                o.getId(), o.getRef(), o.getAddressText(), o.getLat(), o.getLng(),
                o.getTimeWindowStart(), o.getTimeWindowEnd(), o.getLoad(), o.getPriority(),
                o.getNotes(), o.getStatus().name(), o.getExceptionReason(),
                o.getCustomerName(), o.getCustomerPhone(), o.getCreatedAt(), o.getUpdatedAt()
        );
    }
}
