package com.routeflow.repository;

import com.routeflow.domain.LocationPing;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface LocationPingRepository extends MongoRepository<LocationPing, String> {
    Optional<LocationPing> findFirstByVehicleIdOrderByTimestampDesc(String vehicleId);
    List<LocationPing> findByVehicleIdAndTimestampBetweenOrderByTimestampAsc(String vehicleId, Instant from, Instant to);
}
