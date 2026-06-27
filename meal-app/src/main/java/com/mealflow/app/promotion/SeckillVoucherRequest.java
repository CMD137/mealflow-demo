package com.mealflow.app.promotion;

import jakarta.validation.constraints.NotBlank;

public record SeckillVoucherRequest(@NotBlank String requestId) {
}
