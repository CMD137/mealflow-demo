package com.mealflow.fulfillment.api;

import jakarta.validation.constraints.NotBlank;

public record FulfillmentRequest(@NotBlank String requestId, String reason) {
}
