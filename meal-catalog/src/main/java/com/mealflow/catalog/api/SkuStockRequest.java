package com.mealflow.catalog.api;

import jakarta.validation.constraints.Min;

public record SkuStockRequest(@Min(0) int stock) {
}
