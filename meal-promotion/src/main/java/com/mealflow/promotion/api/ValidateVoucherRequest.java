package com.mealflow.promotion.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ValidateVoucherRequest(
    @NotBlank String requestId,
    long userId,
    Long userVoucherId,
    @NotNull Long merchantId
) {
}
