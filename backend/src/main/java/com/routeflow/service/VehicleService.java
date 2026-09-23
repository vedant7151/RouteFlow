package com.routeflow.service;

import com.routeflow.domain.User;
import com.routeflow.domain.Vehicle;
import com.routeflow.dto.vehicle.VehicleRequest;
import com.routeflow.exception.ResourceNotFoundException;
import com.routeflow.repository.UserRepository;
import com.routeflow.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class VehicleService {

    private final VehicleRepository vehicleRepository;
    private final UserRepository userRepository;

    public List<Vehicle> findAll() {
        return vehicleRepository.findAll();
    }

    public Vehicle findById(String id) {
        return vehicleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found: " + id));
    }

    public Vehicle create(VehicleRequest req) {
        Vehicle vehicle = Vehicle.builder()
                .label(req.label())
                .capacity(req.capacity())
                .startDepotLat(req.startDepotLat())
                .startDepotLng(req.startDepotLng())
                .shiftStart(req.shiftStart())
                .shiftEnd(req.shiftEnd())
                .vehicleType(req.vehicleType())
                .active(true)
                .build();
        applyDriver(vehicle, req.driverUserId());
        return vehicleRepository.save(vehicle);
    }

    public Vehicle update(String id, VehicleRequest req) {
        Vehicle vehicle = findById(id);
        vehicle.setLabel(req.label());
        vehicle.setCapacity(req.capacity());
        vehicle.setStartDepotLat(req.startDepotLat());
        vehicle.setStartDepotLng(req.startDepotLng());
        vehicle.setShiftStart(req.shiftStart());
        vehicle.setShiftEnd(req.shiftEnd());
        vehicle.setVehicleType(req.vehicleType());
        applyDriver(vehicle, req.driverUserId());
        return vehicleRepository.save(vehicle);
    }

    public void deactivate(String id) {
        Vehicle vehicle = findById(id);
        vehicle.setActive(false);
        vehicleRepository.save(vehicle);
    }

    private void applyDriver(Vehicle vehicle, String driverUserId) {
        if (driverUserId == null) {
            vehicle.setDriverId(null);
            vehicle.setDriverName(null);
            return;
        }
        User driver = userRepository.findById(driverUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Driver user not found: " + driverUserId));
        vehicle.setDriverId(driver.getId());
        vehicle.setDriverName(driver.getName());
    }
}
