package com.mealflow.promotion.api;

import jakarta.validation.constraints.NotBlank;

public record VoucherTransitionRequest(@NotBlank String requestId, Long voucherLockId, Long orderId, String reason) {
}
