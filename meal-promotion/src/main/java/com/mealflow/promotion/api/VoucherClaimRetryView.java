package com.mealflow.promotion.api;

import java.time.LocalDateTime;

public record VoucherClaimRetryView(
    long retryId,
    long userId,
    long voucherId,
    String status,
    int retryCount,
    int maxRetries,
    String lastError,
    LocalDateTime nextRetryTime
) {
}
