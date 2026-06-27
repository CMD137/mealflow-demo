package com.mealflow.payment.api;

import jakarta.validation.constraints.NotBlank;

public record ClosePaymentRequest(@NotBlank String requestId, String reason) {
}
