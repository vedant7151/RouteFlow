package com.routeflow.repository;

import com.routeflow.domain.Order;
import com.routeflow.domain.enums.OrderStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;

public interface OrderRepository extends MongoRepository<Order, String> {
    List<Order> findByStatus(OrderStatus status);
    List<Order> findByTimeWindowStartBetween(Instant from, Instant to);
    List<Order> findByStatusAndTimeWindowStartBetween(OrderStatus status, Instant from, Instant to);
    List<Order> findByStatusAndUpdatedAtBetween(OrderStatus status, Instant from, Instant to);
}
