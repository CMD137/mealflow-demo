package com.mealflow.promotion.api;

import java.time.LocalDateTime;

public record VoucherClaimRetryView(
    long retryId,
    String eventKey,
    long userId,
    long voucherId,
    String status,
    int retryCount,
    String lastError,
    LocalDateTime nextRetryTime
) {
}
