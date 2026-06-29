package com.mealflow.merchant.api;

import jakarta.validation.constraints.NotBlank;

public record BusinessStatusRequest(@NotBlank String businessStatus) {
}
