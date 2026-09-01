package com.mealflow.promotion.api;

public record UserVoucherView(long userVoucherId, long voucherId, String status, String voucherName,
    int discountCent, String scope, Long merchantId) {
}
