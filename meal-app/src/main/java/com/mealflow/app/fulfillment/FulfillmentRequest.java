package com.mealflow.app.fulfillment;

import jakarta.validation.constraints.NotBlank;

public record FulfillmentRequest(@NotBlank String requestId, String reason) {
}
