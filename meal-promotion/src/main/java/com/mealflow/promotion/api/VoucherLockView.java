package com.mealflow.promotion.api;

public record VoucherLockView(long voucherLockId, long userVoucherId, String status, Long ticketId, Long orderId) {
}
