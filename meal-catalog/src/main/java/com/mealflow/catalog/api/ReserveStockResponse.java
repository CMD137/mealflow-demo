package com.mealflow.catalog.api;

import java.util.List;

public record ReserveStockResponse(List<Long> reservationIds, String status) {
}
