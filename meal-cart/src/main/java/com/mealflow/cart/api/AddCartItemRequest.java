package com.mealflow.cart.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record AddCartItemRequest(@NotNull Long merchantId, @NotNull Long skuId, @Min(1) int quantity) {
}
