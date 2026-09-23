package com.routeflow.domain;

import com.routeflow.domain.enums.StopStatus;
import lombok.*;

import java.time.Instant;

/**
 * Embedded within a Route document (Mongo has no joins, so a stop lives inside its parent
 * route's "stops" array rather than its own top-level collection). Its id is a manually
 * assigned UUID string (RouteStop is never a @Document, so Mongo won't generate one for it).
 *
 * orderRef/addressText/lat/lng are a denormalized snapshot of the Order at the time it was
 * planned onto this route - avoids an Order lookup for every route/stop read, and reflects
 * what was actually planned even if the order is edited afterward.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RouteStop {

    private String id;

    private String orderId;
    private String orderRef;
    private String addressText;
    /** Snapshot of the order's contact + handling info, so the driver view needs no Order lookup. */
    private String customerName;
    private String customerPhone;
    private String notes;
    private double load;
    private double lat;
    private double lng;
    private Instant timeWindowStart;
    private Instant timeWindowEnd;

    private int sequence;

    private Instant plannedEta;
    private Instant actualArrival;

    @Builder.Default
    private StopStatus status = StopStatus.PENDING;

    private double distanceFromPrevKm;
}
