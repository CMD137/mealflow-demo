package com.mealflow.promotion.api;

public record VoucherLockResponse(Long voucherLockId, String status, int discountAmount) {
}
