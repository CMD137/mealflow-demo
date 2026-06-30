package com.mealflow.catalog.api;

import jakarta.validation.constraints.NotBlank;

public record SkuStatusRequest(@NotBlank String status) {
}
