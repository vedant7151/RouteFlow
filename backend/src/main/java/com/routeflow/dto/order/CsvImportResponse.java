package com.routeflow.dto.order;

import java.util.List;

public record CsvImportResponse(
        int importedCount,
        int failedCount,
        List<String> errors
) {
}
