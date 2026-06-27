package com.mealflow.promotion.api;

import jakarta.validation.constraints.NotBlank;

public record SeckillVoucherRequest(@NotBlank String requestId) {
}
