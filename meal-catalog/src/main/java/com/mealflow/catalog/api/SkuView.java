package com.mealflow.catalog.api;

public record SkuView(long skuId, long merchantId, Long categoryId, String categoryName, String name,
    String description, String imageUrl, int priceCent, int stock, String status) {
}
