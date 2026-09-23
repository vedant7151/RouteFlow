package com.routeflow.controller;

import com.routeflow.dto.route.*;
import com.routeflow.service.DispatchService;
import com.routeflow.service.RouteOptimizationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/routes")
@RequiredArgsConstructor
public class RouteController {

    private final RouteOptimizationService routeOptimizationService;
    private final DispatchService dispatchService;

    @GetMapping
    public List<RouteResponse> list(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return dispatchService.findByDate(date).stream().map(RouteResponse::from).toList();
    }

    @GetMapping("/{id}")
    public RouteResponse get(@PathVariable String id) {
        return RouteResponse.from(dispatchService.findById(id));
    }

    @PostMapping("/optimize")
    public OptimizeResponse optimize(@Valid @RequestBody OptimizeRequest request) {
        return routeOptimizationService.optimize(request);
    }

    /** Discards a PLANNED (not yet dispatched) route; its orders go back to PENDING. */
    @DeleteMapping("/{id}")
    public org.springframework.http.ResponseEntity<Void> discard(@PathVariable String id) {
        routeOptimizationService.discardPlannedRoute(id);
        return org.springframework.http.ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/dispatch")
    public RouteResponse dispatch(@PathVariable String id) {
        return RouteResponse.from(dispatchService.dispatch(id));
    }

    @PatchMapping("/{id}/reorder")
    public RouteResponse reorder(@PathVariable String id, @Valid @RequestBody ReorderRequest request) {
        return RouteResponse.from(dispatchService.reorder(id, request));
    }
}
