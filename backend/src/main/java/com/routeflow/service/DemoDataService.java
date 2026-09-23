package com.routeflow.service;

import com.routeflow.config.AppClock;
import com.routeflow.domain.LocationPing;
import com.routeflow.domain.Order;
import com.routeflow.domain.ProofOfDelivery;
import com.routeflow.domain.Route;
import com.routeflow.domain.RouteStop;
import com.routeflow.domain.User;
import com.routeflow.domain.Vehicle;
import com.routeflow.domain.enums.OrderStatus;
import com.routeflow.domain.enums.Role;
import com.routeflow.domain.enums.RouteStatus;
import com.routeflow.domain.enums.StopStatus;
import com.routeflow.repository.LocationPingRepository;
import com.routeflow.repository.OrderRepository;
import com.routeflow.repository.ProofOfDeliveryRepository;
import com.routeflow.repository.RouteRepository;
import com.routeflow.repository.UserRepository;
import com.routeflow.repository.VehicleRepository;
import com.routeflow.util.GeoUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Builds a deterministic Mumbai demo scenario that exercises every PRD feature:
 * <ul>
 *   <li><b>Fleet</b>: three active vehicles with different capacities, depots and shifts, plus an inactive truck
 *       (ignored by the optimizer) and an unassigned driver.</li>
 *   <li><b>Today</b>: 21 PENDING orders - normal, urgent, time-windowed and repeat-customer orders, plus three
 *       deliberately un-routable ones (too heavy, unreachable time window, no coordinates) that show what
 *       "unassigned" and "pin-drop" mean.</li>
 *   <li><b>History</b>: 7 days of completed routes with on-time/late/failed deliveries, proof of delivery
 *       (signature + photo) and GPS pings, so every analytics chart has something to show.</li>
 * </ul>
 * {@link #resetAndSeed()} wipes operational data (orders, routes, PODs, pings, vehicles) - never users.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DemoDataService {

    private static final int HISTORY_DAYS = 7;
    private static final double ROAD_FACTOR = 1.3;
    private static final double HISTORY_SPEED_KMH = 25;

    private final UserRepository userRepository;
    private final VehicleRepository vehicleRepository;
    private final OrderRepository orderRepository;
    private final RouteRepository routeRepository;
    private final ProofOfDeliveryRepository proofOfDeliveryRepository;
    private final LocationPingRepository locationPingRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppClock clock;

    @Value("${routeflow.storage.upload-dir}")
    private String uploadDir;

    public record Summary(int vehicles, int todaysOrders, int historicalRoutes, int historicalOrders,
                          int proofsOfDelivery, int locationPings) {
    }

    private record Place(String name, double lat, double lng) {
    }

    private static final List<Place> PLACES = List.of(
            new Place("Gateway of India, Colaba", 18.9220, 72.8347),
            new Place("Chowpatty Beach, Marine Drive", 18.9544, 72.8146),
            new Place("Haji Ali Dargah, Worli", 18.9827, 72.8089),
            new Place("Phoenix Mills, Lower Parel", 18.9930, 72.8300),
            new Place("Worli Sea Face", 19.0176, 72.8155),
            new Place("Dadar Station (West)", 19.0186, 72.8424),
            new Place("Mahim Fort", 19.0410, 72.8400),
            new Place("Bandra Fort", 19.0421, 72.8189),
            new Place("Jio World Drive, BKC", 19.0655, 72.8686),
            new Place("Phoenix Marketcity, Kurla", 19.0863, 72.8886),
            new Place("Ghatkopar Station", 19.0860, 72.9080),
            new Place("Hiranandani, Powai", 19.1176, 72.9060),
            new Place("MIDC, Andheri East", 19.1136, 72.8697),
            new Place("Lokhandwala, Andheri West", 19.1394, 72.8237),
            new Place("Juhu Beach", 19.0988, 72.8265),
            new Place("Goregaon East", 19.1663, 72.8526),
            new Place("Malad Mindspace", 19.1874, 72.8484)
    );

    private static final List<String> CUSTOMERS = List.of(
            "Aarav Shah", "Diya Mehta", "Kabir Joshi", "Ishita Rao", "Vivaan Nair", "Ananya Iyer",
            "Rohan Kulkarni", "Sara Khan", "Neha Verma", "Arjun Patil", "Meera Desai", "Karan Malhotra",
            "Pooja Bhatt", "Imran Sheikh", "Tanya Gill", "Dev Menon", "Nisha Rao", "Yash Chopra");

    private static final List<String> POD_NOTES = List.of(
            "Handed to customer", "Left with building security", "Signed by receiver",
            "Delivered to reception desk", "Customer collected at gate");

    private static final List<String> FAIL_REASONS = List.of(
            "Customer absent", "Wrong address", "Damaged goods", "Refused by customer");

    // --- users -----------------------------------------------------------------------------------

    /** Idempotent: creates any missing demo account, never touches existing ones (or their passwords). */
    public void ensureDemoUsers() {
        ensureUser("Priya Dispatcher", "dispatcher@routeflow.dev", "Dispatcher@123", Role.DISPATCHER);
        ensureUser("Anil Manager", "manager@routeflow.dev", "Manager@123", Role.MANAGER);
        ensureUser("Ravi Driver", "driver1@routeflow.dev", "Driver@123", Role.DRIVER);
        ensureUser("Sunita Driver", "driver2@routeflow.dev", "Driver@123", Role.DRIVER);
        ensureUser("Imran Driver", "driver3@routeflow.dev", "Driver@123", Role.DRIVER);
    }

    private User ensureUser(String name, String email, String password, Role role) {
        return userRepository.findByEmailIgnoreCase(email).orElseGet(() -> userRepository.save(User.builder()
                .name(name)
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .role(role)
                .enabled(true)
                .build()));
    }

    public boolean hasNoOperationalData() {
        return orderRepository.count() == 0 && routeRepository.count() == 0;
    }

    // --- reset + seed ----------------------------------------------------------------------------

    public Summary resetAndSeed() {
        ensureDemoUsers();

        orderRepository.deleteAll();
        routeRepository.deleteAll();
        proofOfDeliveryRepository.deleteAll();
        locationPingRepository.deleteAll();
        vehicleRepository.deleteAll();

        List<Vehicle> fleet = seedFleet();
        List<Vehicle> activeFleet = fleet.stream().filter(Vehicle::isActive).toList();

        int todaysOrders = seedTodaysOrders();

        Counters counters = new Counters();
        LocalDate today = clock.today();
        for (int daysAgo = HISTORY_DAYS; daysAgo >= 1; daysAgo--) {
            LocalDate date = today.minusDays(daysAgo);
            for (int v = 0; v < activeFleet.size(); v++) {
                // The bike doesn't ride every day - makes the leaderboard and per-day counts less uniform.
                if (v == 2 && daysAgo % 3 == 0) {
                    continue;
                }
                seedHistoricalRoute(date, daysAgo, activeFleet.get(v), new Random(9000L + daysAgo * 31L + v), counters);
            }
        }

        Summary summary = new Summary(fleet.size(), todaysOrders, counters.routes, counters.orders, counters.pods, counters.pings);
        log.info("Demo data seeded: {}", summary);
        return summary;
    }

    private List<Vehicle> seedFleet() {
        User ravi = userRepository.findByEmailIgnoreCase("driver1@routeflow.dev").orElseThrow();
        User sunita = userRepository.findByEmailIgnoreCase("driver2@routeflow.dev").orElseThrow();
        User imran = userRepository.findByEmailIgnoreCase("driver3@routeflow.dev").orElseThrow();

        List<Vehicle> vehicles = new ArrayList<>();
        // Depots are spread out on purpose so "nearest depot" clustering is visible on the map.
        vehicles.add(vehicle("Van-1", "van", 24, 19.1197, 72.8468, LocalTime.of(9, 0), LocalTime.of(18, 0), ravi, true));        // Andheri
        vehicles.add(vehicle("Van-2", "van", 20, 18.9967, 72.8300, LocalTime.of(9, 0), LocalTime.of(17, 0), sunita, true));      // Lower Parel
        vehicles.add(vehicle("Bike-1", "bike", 8, 19.0596, 72.8295, LocalTime.of(10, 0), LocalTime.of(16, 0), imran, true));     // Bandra
        vehicles.add(vehicle("Truck-1", "truck", 80, 19.0863, 72.9081, LocalTime.of(8, 0), LocalTime.of(19, 0), null, false));   // Ghatkopar, inactive
        return vehicleRepository.saveAll(vehicles);
    }

    private Vehicle vehicle(String label, String type, double capacity, double lat, double lng,
                            LocalTime shiftStart, LocalTime shiftEnd, User driver, boolean active) {
        return Vehicle.builder()
                .label(label).vehicleType(type).capacity(capacity)
                .startDepotLat(lat).startDepotLng(lng)
                .shiftStart(shiftStart).shiftEnd(shiftEnd)
                .driverId(driver != null ? driver.getId() : null)
                .driverName(driver != null ? driver.getName() : null)
                .active(active)
                .build();
    }

    // --- today's orders --------------------------------------------------------------------------

    private int seedTodaysOrders() {
        LocalDate today = clock.today();
        List<Order> orders = new ArrayList<>();

        //            ref      customer          phone           address                                lat      lng      load pri window            notes
        orders.add(demoOrder("T-101", "Aarav Shah", "+91 98200 10101", "Gateway of India, Colaba", 18.9220, 72.8347, 3, 1, null, null, "Fragile - handle with care"));
        orders.add(demoOrder("T-102", "Diya Mehta", "+91 98200 10102", "Chowpatty Beach, Marine Drive", 18.9544, 72.8146, 2, 0, w(today, 10, 0), w(today, 13, 0), "Delivery window 10:00-13:00"));
        orders.add(demoOrder("T-103", "Kabir Joshi", "+91 98200 10103", "Haji Ali Dargah, Worli", 18.9827, 72.8089, 4, 2, null, null, "URGENT - medicines"));
        orders.add(demoOrder("T-104", "Ishita Rao", "+91 98200 10104", "Phoenix Mills, Lower Parel", 18.9930, 72.8300, 5, 0, w(today, 11, 0), w(today, 14, 0), "Delivery window 11:00-14:00"));
        orders.add(demoOrder("T-105", "Vivaan Nair", "+91 98200 10105", "Worli Sea Face", 19.0176, 72.8155, 2, 1, null, null, null));
        orders.add(demoOrder("T-106", "Ananya Iyer", "+91 98200 10106", "Dadar Station (West)", 19.0186, 72.8424, 1, 0, null, null, "Call on arrival"));
        orders.add(demoOrder("T-107", "Rohan Kulkarni", "+91 98200 10107", "Mahim Fort", 19.0410, 72.8400, 3, 0, w(today, 12, 0), w(today, 15, 0), "Delivery window 12:00-15:00"));
        orders.add(demoOrder("T-108", "Sara Khan", "+91 98200 10108", "Bandra Fort", 19.0421, 72.8189, 2, 2, null, null, "URGENT - same-day document"));
        orders.add(demoOrder("T-109", "Aarav Shah", "+91 98200 10101", "Jio World Drive, BKC", 19.0655, 72.8686, 6, 0, null, null, "Second order for the same customer (see Customers page)"));
        orders.add(demoOrder("T-110", "Neha Verma", "+91 98200 10110", "Phoenix Marketcity, Kurla", 19.0863, 72.8886, 2, 0, null, null, null));
        orders.add(demoOrder("T-111", "Arjun Patil", "+91 98200 10111", "Ghatkopar Station", 19.0860, 72.9080, 4, 1, w(today, 14, 0), w(today, 17, 0), "Delivery window 14:00-17:00"));
        orders.add(demoOrder("T-112", "Meera Desai", "+91 98200 10112", "Hiranandani, Powai", 19.1176, 72.9060, 3, 0, null, null, null));
        orders.add(demoOrder("T-113", "Karan Malhotra", "+91 98200 10113", "Godrej One, Vikhroli", 19.1116, 72.9271, 2, 0, null, null, null));
        orders.add(demoOrder("T-114", "Pooja Bhatt", "+91 98200 10114", "MIDC, Andheri East", 19.1136, 72.8697, 1, 1, null, null, null));
        orders.add(demoOrder("T-115", "Imran Sheikh", "+91 98200 10115", "Lokhandwala, Andheri West", 19.1394, 72.8237, 2, 0, null, null, null));
        orders.add(demoOrder("T-116", "Tanya Gill", "+91 98200 10116", "Juhu Beach", 19.0988, 72.8265, 1, 0, w(today, 10, 0), w(today, 12, 0), "Delivery window 10:00-12:00"));
        orders.add(demoOrder("T-117", "Dev Menon", "+91 98200 10117", "Goregaon East", 19.1663, 72.8526, 3, 0, null, null, null));
        orders.add(demoOrder("T-118", "Nisha Rao", "+91 98200 10118", "Malad Mindspace", 19.1874, 72.8484, 2, 1, null, null, null));

        // Deliberately un-routable - each shows a different reason an order can stay unassigned.
        orders.add(demoOrder("T-201", "Reliance Retail (bulk)", "+91 98200 10201", "Kurla Warehouse, Mumbai", 19.0728, 72.8826, 90, 1, null, null,
                "DEMO: load 90 exceeds every vehicle's capacity, so Optimize leaves it unassigned"));
        orders.add(demoOrder("T-202", "Rajesh Trivedi", "+91 98200 10202", "Borivali National Park Gate", 19.2307, 72.8567, 1, 0, w(today, 9, 0), w(today, 9, 5),
                "DEMO: window closes 09:05 - no vehicle can reach it in time, so it stays unassigned"));
        orders.add(demoOrder("T-203", "Farah Siddiqui", "+91 98200 10203", "Flat 12, Sea View Apartments, Worli", null, null, 1, 0, null, null,
                "DEMO: no coordinates - on the Orders page, click the map to drop a pin, then save"));

        orderRepository.saveAll(orders);
        return orders.size();
    }

    private Instant w(LocalDate day, int hour, int minute) {
        return clock.at(day, LocalTime.of(hour, minute));
    }

    private Order demoOrder(String ref, String customer, String phone, String address, Double lat, Double lng,
                        double load, int priority, Instant windowStart, Instant windowEnd, String notes) {
        return Order.builder()
                .ref(ref).customerName(customer).customerPhone(phone).addressText(address)
                .lat(lat).lng(lng).load(load).priority(priority)
                .timeWindowStart(windowStart).timeWindowEnd(windowEnd)
                .notes(notes)
                .status(OrderStatus.PENDING)
                .build();
    }

    // --- history ---------------------------------------------------------------------------------

    private static class Counters {
        int routes;
        int orders;
        int pods;
        int pings;
    }

    private void seedHistoricalRoute(LocalDate date, int daysAgo, Vehicle vehicle, Random rnd, Counters counters) {
        Instant shiftStart = clock.at(date, vehicle.getShiftStart());
        // Service improves over the week: ~40% late deliveries 7 days ago, ~10% yesterday - gives the trend chart a shape.
        double lateProbability = 0.08 + 0.05 * (daysAgo - 1);

        int stopCount = 3 + rnd.nextInt(3);
        List<Place> places = new ArrayList<>(PLACES);
        java.util.Collections.shuffle(places, rnd);
        List<Place> chosen = nearestNeighbourOrder(vehicle, places.subList(0, stopCount));

        Route route = Route.builder()
                .vehicleId(vehicle.getId())
                .vehicleLabel(vehicle.getLabel())
                .date(date)
                .status(RouteStatus.COMPLETED)
                .createdAt(shiftStart.minusSeconds(3600))
                .dispatchedAt(shiftStart)
                .build();

        double lat = vehicle.getStartDepotLat();
        double lng = vehicle.getStartDepotLng();
        Instant plannedClock = shiftStart;
        Instant actualClock = shiftStart;
        double plannedKm = 0;
        double plannedMin = 0;
        List<String> polyline = new ArrayList<>();
        polyline.add(String.format(java.util.Locale.ROOT, "%.6f,%.6f", lat, lng));
        List<LocationPing> pings = new ArrayList<>();

        for (int i = 0; i < chosen.size(); i++) {
            Place place = chosen.get(i);
            double km = GeoUtils.haversineKm(lat, lng, place.lat(), place.lng()) * ROAD_FACTOR;
            double legMin = km / HISTORY_SPEED_KMH * 60.0;

            Instant eta = plannedClock.plusSeconds((long) (legMin * 60));
            boolean late = rnd.nextDouble() < lateProbability;
            long deltaMin = late ? 12 + rnd.nextInt(24) : rnd.nextInt(13) - 5; // late: +12..+35, on time: -5..+7
            Instant arrival = eta.plusSeconds(deltaMin * 60);
            if (arrival.isBefore(actualClock.plusSeconds((long) (legMin * 45)))) {
                arrival = actualClock.plusSeconds((long) (legMin * 45)); // can't teleport
            }

            boolean failed = rnd.nextDouble() < 0.07;
            Order order = orderRepository.save(Order.builder()
                    .ref(String.format("H-%s-%s%d", date.format(DateTimeFormatter.BASIC_ISO_DATE), vehicle.getLabel().charAt(0), i + 1))
                    .customerName(CUSTOMERS.get(rnd.nextInt(CUSTOMERS.size())))
                    .customerPhone("+91 98200 2" + String.format("%04d", rnd.nextInt(10000)))
                    .addressText(place.name())
                    .lat(place.lat()).lng(place.lng())
                    .load(1 + rnd.nextInt(4)).priority(rnd.nextInt(3))
                    .status(failed ? OrderStatus.FAILED : OrderStatus.DELIVERED)
                    .exceptionReason(failed ? FAIL_REASONS.get(rnd.nextInt(FAIL_REASONS.size())) : null)
                    .createdAt(clock.at(date, LocalTime.of(7, 30)))
                    .updatedAt(arrival)
                    .build());
            counters.orders++;

            route.getStops().add(RouteStop.builder()
                    .id(UUID.randomUUID().toString())
                    .orderId(order.getId())
                    .orderRef(order.getRef())
                    .addressText(order.getAddressText())
                    .customerName(order.getCustomerName())
                    .customerPhone(order.getCustomerPhone())
                    .load(order.getLoad())
                    .lat(place.lat()).lng(place.lng())
                    .sequence(i + 1)
                    .plannedEta(eta)
                    .actualArrival(arrival)
                    .status(failed ? StopStatus.FAILED : StopStatus.DELIVERED)
                    .distanceFromPrevKm(Math.round(km * 100.0) / 100.0)
                    .build());

            addPings(pings, vehicle.getId(), lat, lng, place.lat(), place.lng(), actualClock, arrival, rnd);

            if (!failed) {
                seedProofOfDelivery(order, place, arrival, rnd);
                counters.pods++;
            }

            plannedKm += km;
            plannedMin += legMin;
            polyline.add(String.format(java.util.Locale.ROOT, "%.6f,%.6f", place.lat(), place.lng()));
            lat = place.lat();
            lng = place.lng();
            plannedClock = eta.plusSeconds(5 * 60);
            actualClock = arrival.plusSeconds(5 * 60);
        }

        route.setPlannedDistanceKm(Math.round(plannedKm * 100.0) / 100.0);
        route.setPlannedDurationMin(Math.round(plannedMin * 100.0) / 100.0);
        route.setPolyline(String.join(";", polyline));
        routeRepository.save(route);
        locationPingRepository.saveAll(pings);

        counters.routes++;
        counters.pings += pings.size();
    }

    private List<Place> nearestNeighbourOrder(Vehicle vehicle, List<Place> places) {
        List<Place> remaining = new ArrayList<>(places);
        List<Place> ordered = new ArrayList<>();
        double lat = vehicle.getStartDepotLat();
        double lng = vehicle.getStartDepotLng();
        while (!remaining.isEmpty()) {
            final double fromLat = lat;
            final double fromLng = lng;
            Place next = remaining.stream()
                    .min(Comparator.comparingDouble(p -> GeoUtils.haversineKm(fromLat, fromLng, p.lat(), p.lng())))
                    .orElseThrow();
            ordered.add(next);
            remaining.remove(next);
            lat = next.lat();
            lng = next.lng();
        }
        return ordered;
    }

    /** GPS breadcrumbs along the straight leg with ~40m of jitter, so "actual km" is real summed ping distance. */
    private void addPings(List<LocationPing> pings, String vehicleId, double fromLat, double fromLng,
                          double toLat, double toLng, Instant start, Instant end, Random rnd) {
        double legKm = GeoUtils.haversineKm(fromLat, fromLng, toLat, toLng);
        int count = Math.max(3, (int) (legKm / 0.5));
        long spanSeconds = Math.max(60, end.getEpochSecond() - start.getEpochSecond());

        for (int i = 1; i <= count; i++) {
            double f = (double) i / count;
            double jitterLat = (rnd.nextDouble() - 0.5) * 0.0008;
            double jitterLng = (rnd.nextDouble() - 0.5) * 0.0008;
            boolean last = i == count;
            pings.add(LocationPing.builder()
                    .vehicleId(vehicleId)
                    .lat(last ? toLat : fromLat + (toLat - fromLat) * f + jitterLat)
                    .lng(last ? toLng : fromLng + (toLng - fromLng) * f + jitterLng)
                    .speed(20.0 + rnd.nextInt(15))
                    .timestamp(start.plusSeconds((long) (spanSeconds * f)))
                    .build());
        }
    }

    // --- proof of delivery -----------------------------------------------------------------------

    private void seedProofOfDelivery(Order order, Place place, Instant arrival, Random rnd) {
        String photoUrl = rnd.nextDouble() < 0.4 ? writeDemoPhoto(rnd) : null;
        String signature = rnd.nextDouble() < 0.75 ? drawDemoSignature(rnd) : null;

        proofOfDeliveryRepository.save(ProofOfDelivery.builder()
                .orderId(order.getId())
                .photoUrl(photoUrl)
                .signatureData(signature)
                .notes(POD_NOTES.get(rnd.nextInt(POD_NOTES.size())))
                .capturedLat(place.lat())
                .capturedLng(place.lng())
                .capturedAt(arrival)
                .build());
    }

    private String drawDemoSignature(Random rnd) {
        BufferedImage img = new BufferedImage(240, 80, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(31, 41, 55));
            g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            int x = 15;
            int y = 40;
            for (int i = 0; i < 9; i++) {
                int nx = x + 18 + rnd.nextInt(12);
                int ny = 18 + rnd.nextInt(44);
                g.drawLine(x, y, nx, ny);
                x = nx;
                y = ny;
            }
        } finally {
            g.dispose();
        }
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(toPng(img));
    }

    /** A stand-in "parcel at the door" photo, written where PodService stores real uploads. */
    private String writeDemoPhoto(Random rnd) {
        try {
            BufferedImage img = new BufferedImage(320, 240, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setPaint(new GradientPaint(0, 0, new Color(226, 232, 240), 0, 240, new Color(148, 163, 184)));
                g.fillRect(0, 0, 320, 240);
                g.setColor(new Color(180 + rnd.nextInt(40), 130 + rnd.nextInt(30), 80));
                g.fillRoundRect(95, 95, 130, 100, 8, 8);
                g.setColor(new Color(120, 85, 50));
                g.fillRect(95, 135, 130, 10);
                g.fillRect(155, 95, 10, 100);
            } finally {
                g.dispose();
            }
            Path dir = Path.of(uploadDir, "pod");
            Files.createDirectories(dir);
            String filename = "seed-" + UUID.randomUUID() + ".png";
            Files.write(dir.resolve(filename), toPng(img));
            return "/uploads/pod/" + filename;
        } catch (IOException | RuntimeException ex) {
            log.warn("Could not write demo POD photo: {}", ex.getMessage());
            return null;
        }
    }

    private byte[] toPng(BufferedImage img) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(img, "png", out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
