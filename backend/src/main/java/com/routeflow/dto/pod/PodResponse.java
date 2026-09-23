package com.routeflow.dto.pod;

import com.routeflow.domain.ProofOfDelivery;

import java.time.Instant;

public record PodResponse(
        String id,
        String orderId,
        String photoUrl,
        boolean hasSignature,
        /** Drawn/typed signature as a data URL, so the audit view can render it. */
        String signatureData,
        String notes,
        Double capturedLat,
        Double capturedLng,
        Instant capturedAt
) {
    public static PodResponse from(ProofOfDelivery p) {
        return new PodResponse(
                p.getId(), p.getOrderId(), p.getPhotoUrl(),
                p.getSignatureData() != null && !p.getSignatureData().isBlank(),
                p.getSignatureData(),
                p.getNotes(), p.getCapturedLat(), p.getCapturedLng(), p.getCapturedAt()
        );
    }
}
