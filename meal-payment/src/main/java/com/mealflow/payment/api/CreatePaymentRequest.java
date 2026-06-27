package com.mealflow.payment.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CreatePaymentRequest(@NotBlank String requestId, long orderId, @Min(0) int amountCent) {
}
