package com.mealflow.catalog.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record SkuAdminRequest(
    Long categoryId,
    @NotBlank String name,
    String description,
    String imageUrl,
    @Min(1) int priceCent,
    @Min(0) int stock,
    String status
) {
}
