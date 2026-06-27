package com.mealflow.promotion.api;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;

public record LockVoucherRequest(
    @NotBlank String requestId,
    long userId,
    Long userVoucherId,
    Long ticketId,
    Long orderId,
    LocalDateTime lockExpireTime
) {
}
