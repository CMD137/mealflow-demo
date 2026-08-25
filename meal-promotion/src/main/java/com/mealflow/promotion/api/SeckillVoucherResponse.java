package com.mealflow.promotion.api;

public record SeckillVoucherResponse(String eventKey, String status, Long claimId, Long userVoucherId) {
}
