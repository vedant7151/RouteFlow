package com.routeflow.dto.analytics;

import java.util.List;
import java.util.Map;

public record AnalyticsSummaryResponse(
        long deliveriesCompleted,
        long deliveriesFailed,
        double onTimePercentage,
        double avgDeliveryTimeMinutes,
        double totalPlannedKm,
        double totalActualKm,
        List<DailyCount> deliveriesPerDay,
        List<DailyOnTime> onTimeTrend,
        List<DailyDistance> distancePerDay,
        List<DriverLeaderboardEntry> driverLeaderboard,
        List<FailureReason> failureReasons,
        Map<String, Long> ordersByStatus
) {
    public record DailyCount(String date, long count) {
    }

    public record DailyOnTime(String date, double percentage, long delivered) {
    }

    public record DailyDistance(String date, double plannedKm, double actualKm) {
    }

    public record DriverLeaderboardEntry(String vehicleId, String label, String driverName, long delivered, double km) {
    }

    public record FailureReason(String reason, long count) {
    }
}
