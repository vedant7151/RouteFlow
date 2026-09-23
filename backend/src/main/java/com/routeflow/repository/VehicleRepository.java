package com.routeflow.repository;

import com.routeflow.domain.Vehicle;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface VehicleRepository extends MongoRepository<Vehicle, String> {
    List<Vehicle> findByActiveTrue();
    Optional<Vehicle> findByDriverId(String driverUserId);
}
