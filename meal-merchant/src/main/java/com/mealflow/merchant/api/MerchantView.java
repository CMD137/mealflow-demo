package com.mealflow.merchant.api;

public record MerchantView(long merchantId, String name, String businessStatus, int baseCapacity, double manualFactor) {
}
