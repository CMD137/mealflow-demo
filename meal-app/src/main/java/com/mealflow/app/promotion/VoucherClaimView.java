package com.mealflow.app.promotion;

public record VoucherClaimView(long claimId, long userId, long voucherId, String status) {
}
