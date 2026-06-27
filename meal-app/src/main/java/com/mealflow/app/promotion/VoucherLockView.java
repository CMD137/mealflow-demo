package com.mealflow.app.promotion;

public record VoucherLockView(long voucherLockId, long userVoucherId, String status, Long ticketId, Long orderId) {
}
