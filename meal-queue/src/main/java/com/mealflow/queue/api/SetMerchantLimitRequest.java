package com.mealflow.queue.api;

import jakarta.validation.constraints.Min;

public record SetMerchantLimitRequest(@Min(1) int limit) {
}
