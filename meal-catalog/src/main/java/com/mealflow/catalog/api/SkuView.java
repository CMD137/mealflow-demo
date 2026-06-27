package com.mealflow.catalog.api;

public record SkuView(long skuId, long merchantId, String name, int priceCent, int stock) {
}
