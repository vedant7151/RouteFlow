package com.routeflow.controller;

import com.routeflow.dto.vehicle.VehicleRequest;
import com.routeflow.dto.vehicle.VehicleResponse;
import com.routeflow.service.VehicleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/vehicles")
@RequiredArgsConstructor
public class VehicleController {

    private final VehicleService vehicleService;

    @GetMapping
    public List<VehicleResponse> list() {
        return vehicleService.findAll().stream().map(VehicleResponse::from).toList();
    }

    @GetMapping("/{id}")
    public VehicleResponse get(@PathVariable String id) {
        return VehicleResponse.from(vehicleService.findById(id));
    }

    @PostMapping
    public ResponseEntity<VehicleResponse> create(@Valid @RequestBody VehicleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(VehicleResponse.from(vehicleService.create(request)));
    }

    @PutMapping("/{id}")
    public VehicleResponse update(@PathVariable String id, @Valid @RequestBody VehicleRequest request) {
        return VehicleResponse.from(vehicleService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable String id) {
        vehicleService.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
