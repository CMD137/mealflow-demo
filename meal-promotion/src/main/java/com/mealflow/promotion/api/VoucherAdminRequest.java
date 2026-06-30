package com.mealflow.promotion.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record VoucherAdminRequest(
    @NotBlank String name,
    @NotBlank String type,
    @Min(1) int discountCent,
    @Min(0) int stock,
    String status
) {
}
