package com.routeflow.dto.route;

import com.routeflow.domain.RouteStop;

import java.time.Instant;

public record RouteStopResponse(
        String id,
        String orderId,
        String orderRef,
        String addressText,
        String customerName,
        String customerPhone,
        String notes,
        double load,
        Instant timeWindowStart,
        Instant timeWindowEnd,
        Double lat,
        Double lng,
        int sequence,
        Instant plannedEta,
        Instant actualArrival,
        String status,
        double distanceFromPrevKm
) {
    public static RouteStopResponse from(RouteStop s) {
        return new RouteStopResponse(
                s.getId(),
                s.getOrderId(),
                s.getOrderRef(),
                s.getAddressText(),
                s.getCustomerName(),
                s.getCustomerPhone(),
                s.getNotes(),
                s.getLoad(),
                s.getTimeWindowStart(),
                s.getTimeWindowEnd(),
                s.getLat(),
                s.getLng(),
                s.getSequence(),
                s.getPlannedEta(),
                s.getActualArrival(),
                s.getStatus().name(),
                s.getDistanceFromPrevKm()
        );
    }
}
