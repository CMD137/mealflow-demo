package com.mealflow.catalog.api;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record StockTransitionRequest(@NotBlank String requestId, List<Long> reservationIds, Long orderId, String reason) {
}
