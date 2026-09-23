package com.routeflow.repository;

import com.routeflow.domain.Route;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RouteRepository extends MongoRepository<Route, String> {
    List<Route> findByDate(LocalDate date);
    /** Inclusive on both ends. (Spring Data's derived findByDateBetween is exclusive on MongoDB, which made from==to return nothing.) */
    @Query("{ 'date': { $gte: ?0, $lte: ?1 } }")
    List<Route> findInDateRange(LocalDate from, LocalDate to);
    List<Route> findByVehicleId(String vehicleId);
    Optional<Route> findByVehicleIdAndDate(String vehicleId, LocalDate date);

    /** Finds the route that currently embeds a stop with this id (property path stops.id). */
    Optional<Route> findByStopsId(String stopId);

    /** Every route that has (or had) a stop for this order. */
    List<Route> findByStopsOrderId(String orderId);
}
