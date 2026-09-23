package com.routeflow.service;

import com.routeflow.domain.Order;
import com.routeflow.domain.enums.OrderStatus;
import com.routeflow.dto.order.CsvImportResponse;
import com.routeflow.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Bulk order import from CSV. Expected headers (case-insensitive):
 * ref,addressText,lat,lng,timeWindowStart,timeWindowEnd,load,priority,notes,customerName,customerPhone
 * lat/lng are optional - the row is geocoded via Nominatim when omitted.
 * timeWindowStart/End must be ISO-8601 instants (e.g. 2025-06-01T09:00:00Z), or blank.
 */
@Service
@RequiredArgsConstructor
public class CsvImportService {

    private final OrderRepository orderRepository;
    private final GeocodingService geocodingService;

    public CsvImportResponse importCsv(MultipartFile file) {
        List<String> errors = new ArrayList<>();
        int imported = 0;
        int failed = 0;

        try (CSVParser parser = CSVParser.parse(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8),
                CSVFormat.DEFAULT.builder().setHeader().setIgnoreHeaderCase(true).setTrim(true).build()
        )) {
            for (CSVRecord record : parser) {
                try {
                    Order order = parseRecord(record);
                    orderRepository.save(order);
                    imported++;
                } catch (Exception rowEx) {
                    failed++;
                    errors.add("Row " + record.getRecordNumber() + ": " + rowEx.getMessage());
                }
            }
        } catch (Exception ex) {
            errors.add("Failed to parse CSV: " + ex.getMessage());
        }

        return new CsvImportResponse(imported, failed, errors);
    }

    private Order parseRecord(CSVRecord record) {
        String ref = require(record, "ref");
        String addressText = require(record, "addressText");

        Double lat = parseDoubleOrNull(record, "lat");
        Double lng = parseDoubleOrNull(record, "lng");

        if (lat == null || lng == null) {
            Optional<GeocodingService.GeocodeResult> geocoded = geocodingService.geocode(addressText);
            if (geocoded.isPresent()) {
                lat = geocoded.get().lat();
                lng = geocoded.get().lng();
            }
        }

        return Order.builder()
                .ref(ref)
                .addressText(addressText)
                .lat(lat)
                .lng(lng)
                .timeWindowStart(parseInstantOrNull(record, "timeWindowStart"))
                .timeWindowEnd(parseInstantOrNull(record, "timeWindowEnd"))
                .load(parseDoubleOrNull(record, "load") != null ? parseDoubleOrNull(record, "load") : 1.0)
                .priority(parseIntOrNull(record, "priority") != null ? parseIntOrNull(record, "priority") : 0)
                .notes(optional(record, "notes"))
                .customerName(optional(record, "customerName"))
                .customerPhone(optional(record, "customerPhone"))
                .status(OrderStatus.PENDING)
                .build();
    }

    private String require(CSVRecord record, String column) {
        String value = optional(record, column);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required column '" + column + "'");
        }
        return value;
    }

    private String optional(CSVRecord record, String column) {
        if (!record.isMapped(column)) {
            return null;
        }
        String value = record.get(column);
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private Double parseDoubleOrNull(CSVRecord record, String column) {
        String value = optional(record, column);
        return value == null ? null : Double.parseDouble(value);
    }

    private Integer parseIntOrNull(CSVRecord record, String column) {
        String value = optional(record, column);
        return value == null ? null : Integer.parseInt(value);
    }

    private Instant parseInstantOrNull(CSVRecord record, String column) {
        String value = optional(record, column);
        return value == null ? null : Instant.parse(value);
    }
}
