package com.mealflow.catalog.api;

public record CategoryView(long categoryId, long merchantId, String name, int sortOrder, String status) {
}
