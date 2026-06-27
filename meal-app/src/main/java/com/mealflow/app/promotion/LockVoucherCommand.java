package com.mealflow.app.promotion;

import java.time.LocalDateTime;

public record LockVoucherCommand(
    String requestId,
    long userId,
    Long userVoucherId,
    Long ticketId,
    Long orderId,
    LocalDateTime lockExpireTime
) {
}
