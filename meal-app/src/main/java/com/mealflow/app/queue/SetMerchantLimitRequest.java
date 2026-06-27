package com.mealflow.app.queue;

import jakarta.validation.constraints.Min;

public record SetMerchantLimitRequest(@Min(1) int limit) {
}
