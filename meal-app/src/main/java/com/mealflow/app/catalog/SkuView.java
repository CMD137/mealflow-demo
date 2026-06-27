package com.mealflow.app.catalog;

public record SkuView(long skuId, long merchantId, String name, int priceCent, int stock) {
}
