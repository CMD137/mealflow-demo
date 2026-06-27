package com.mealflow.order.api;

public record OrderItemSnapshot(long skuId, String skuName, int priceCent, int quantity) {
  public int subtotalCent() {
    return priceCent * quantity;
  }
}
