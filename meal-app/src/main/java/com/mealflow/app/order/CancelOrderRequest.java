package com.mealflow.app.order;

import jakarta.validation.constraints.NotBlank;

public record CancelOrderRequest(@NotBlank String requestId, String reason) {
}
