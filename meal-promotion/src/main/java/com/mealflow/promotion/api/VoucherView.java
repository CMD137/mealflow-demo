package com.mealflow.promotion.api;

public record VoucherView(long voucherId, String name, String type, int discountCent, int stock, String status) {
}
