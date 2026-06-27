package com.mealflow.cart.api;

public record CartItemView(long cartItemId, long userId, long merchantId, long skuId, int quantity, boolean selected) {
}
