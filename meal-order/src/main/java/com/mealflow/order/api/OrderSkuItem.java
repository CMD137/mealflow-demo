package com.mealflow.order.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record OrderSkuItem(@NotNull Long skuId, @Min(1) int quantity) {
}
