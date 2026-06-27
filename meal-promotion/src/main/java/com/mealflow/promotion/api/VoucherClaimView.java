package com.mealflow.promotion.api;

public record VoucherClaimView(long claimId, long userId, long voucherId, String status) {
}
