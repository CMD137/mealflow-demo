package com.mealflow.merchant.api;

import jakarta.validation.constraints.NotBlank;

public record SystemMerchantStatusRequest(@NotBlank String businessStatus) {
}
