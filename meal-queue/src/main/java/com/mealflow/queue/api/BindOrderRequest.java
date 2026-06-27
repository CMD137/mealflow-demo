package com.mealflow.queue.api;

import jakarta.validation.constraints.NotBlank;

public record BindOrderRequest(@NotBlank String requestId, long orderId) {
}
