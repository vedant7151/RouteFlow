package com.routeflow.service;

import com.routeflow.config.AppClock;
import com.routeflow.domain.Order;
import com.routeflow.domain.enums.OrderStatus;
import com.routeflow.dto.order.OrderRequest;
import com.routeflow.exception.ResourceNotFoundException;
import com.routeflow.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final GeocodingService geocodingService;
    private final AppClock clock;

    public List<Order> findAll(LocalDate date) {
        if (date == null) {
            return orderRepository.findAll();
        }
        Instant from = clock.startOfDay(date);
        Instant to = clock.startOfDay(date.plusDays(1));
        return orderRepository.findByTimeWindowStartBetween(from, to);
    }

    public Order findById(String id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + id));
    }

    public Order create(OrderRequest req) {
        Instant now = Instant.now();
        Order order = Order.builder()
                .ref(req.ref())
                .addressText(req.addressText())
                .lat(req.lat())
                .lng(req.lng())
                .timeWindowStart(req.timeWindowStart())
                .timeWindowEnd(req.timeWindowEnd())
                .load(req.load() != null ? req.load() : 1.0)
                .priority(req.priority() != null ? req.priority() : 0)
                .notes(req.notes())
                .customerName(req.customerName())
                .customerPhone(req.customerPhone())
                .status(OrderStatus.PENDING)
                .createdAt(now)
                .updatedAt(now)
                .build();

        geocodeIfMissing(order);
        return orderRepository.save(order);
    }

    public Order update(String id, OrderRequest req) {
        Order order = findById(id);
        order.setRef(req.ref());
        order.setAddressText(req.addressText());
        order.setLat(req.lat());
        order.setLng(req.lng());
        order.setTimeWindowStart(req.timeWindowStart());
        order.setTimeWindowEnd(req.timeWindowEnd());
        order.setLoad(req.load() != null ? req.load() : order.getLoad());
        order.setPriority(req.priority() != null ? req.priority() : order.getPriority());
        order.setNotes(req.notes());
        order.setCustomerName(req.customerName());
        order.setCustomerPhone(req.customerPhone());
        order.setUpdatedAt(Instant.now());

        geocodeIfMissing(order);
        return orderRepository.save(order);
    }

    public void delete(String id) {
        Order order = findById(id);
        orderRepository.delete(order);
    }

    public Order updateStatus(String id, OrderStatus status, String exceptionReason) {
        Order order = findById(id);
        order.setStatus(status);
        order.setExceptionReason(status == OrderStatus.FAILED ? exceptionReason : null);
        order.setUpdatedAt(Instant.now());
        return orderRepository.save(order);
    }

    private void geocodeIfMissing(Order order) {
        if (order.getLat() == null || order.getLng() == null) {
            Optional<GeocodingService.GeocodeResult> result = geocodingService.geocode(order.getAddressText());
            result.ifPresent(r -> {
                order.setLat(r.lat());
                order.setLng(r.lng());
            });
        }
    }
}
