package com.mealflow.promotion.api;

import java.time.LocalDateTime;

public record VoucherView(long voucherId, String name, String type, int discountCent, int stock, String status,
    String scope, Long merchantId, LocalDateTime startTime, LocalDateTime endTime) {
}
