package com.mealflow.cart.api;

import jakarta.validation.constraints.Min;

public record UpdateCartItemRequest(@Min(0) int quantity) {
}
