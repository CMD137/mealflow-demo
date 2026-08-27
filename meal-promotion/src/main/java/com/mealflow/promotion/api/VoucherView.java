package com.mealflow.promotion.api;

import java.time.LocalDateTime;

public record VoucherView(long voucherId, String name, String type, int discountCent, int stock, String status,
    LocalDateTime startTime, LocalDateTime endTime) {
}
