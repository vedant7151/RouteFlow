package com.routeflow.service;

import com.routeflow.domain.Order;
import com.routeflow.domain.ProofOfDelivery;
import com.routeflow.exception.BadRequestException;
import com.routeflow.exception.ResourceNotFoundException;
import com.routeflow.repository.ProofOfDeliveryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Stores proof-of-delivery photos on local disk (free, no cloud storage dependency) under
 * routeflow.storage.upload-dir, served back via /uploads/** (see WebMvcConfig). Signatures are
 * kept as a base64/data-URL string directly on the document - small enough not to need a file.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PodService {

    private final ProofOfDeliveryRepository proofOfDeliveryRepository;
    private final OrderService orderService;

    @Value("${routeflow.storage.upload-dir}")
    private String uploadDir;

    public ProofOfDelivery capture(
            String orderId,
            MultipartFile photo,
            String signatureData,
            String notes,
            Double capturedLat,
            Double capturedLng
    ) {
        Order order = orderService.findById(orderId);

        String photoUrl = null;
        if (photo != null && !photo.isEmpty()) {
            photoUrl = storePhoto(orderId, photo);
        }

        ProofOfDelivery pod = proofOfDeliveryRepository.findByOrderId(orderId).orElse(
                ProofOfDelivery.builder().orderId(order.getId()).build());

        if (photoUrl != null) {
            pod.setPhotoUrl(photoUrl);
        }
        pod.setSignatureData(signatureData);
        pod.setNotes(notes);
        pod.setCapturedLat(capturedLat);
        pod.setCapturedLng(capturedLng);
        pod.setCapturedAt(Instant.now());

        return proofOfDeliveryRepository.save(pod);
    }

    public ProofOfDelivery findByOrder(String orderId) {
        return proofOfDeliveryRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("No proof of delivery for order " + orderId));
    }

    public Optional<ProofOfDelivery> findByOrderOptional(String orderId) {
        return proofOfDeliveryRepository.findByOrderId(orderId);
    }

    private String storePhoto(String orderId, MultipartFile photo) {
        try {
            Path dir = Path.of(uploadDir, "pod");
            Files.createDirectories(dir);

            String extension = Optional.ofNullable(photo.getOriginalFilename())
                    .filter(name -> name.contains("."))
                    .map(name -> name.substring(name.lastIndexOf('.')))
                    .orElse(".jpg");
            String filename = "order-" + orderId + "-" + UUID.randomUUID() + extension;
            Path target = dir.resolve(filename);
            Files.copy(photo.getInputStream(), target);

            return "/uploads/pod/" + filename;
        } catch (IOException ex) {
            log.error("Failed to store POD photo for order {}", orderId, ex);
            throw new BadRequestException("Failed to store photo: " + ex.getMessage());
        }
    }
}
