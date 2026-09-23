package com.routeflow.repository;

import com.routeflow.domain.ProofOfDelivery;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface ProofOfDeliveryRepository extends MongoRepository<ProofOfDelivery, String> {
    Optional<ProofOfDelivery> findByOrderId(String orderId);
}
