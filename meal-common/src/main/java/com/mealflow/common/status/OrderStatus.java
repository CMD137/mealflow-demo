package com.mealflow.common.status;

public enum OrderStatus implements CodeEnum {
  PENDING_PAYMENT(1),
  WAIT_MERCHANT_ACCEPT(2),
  MERCHANT_ACCEPTED(3),
  COOKING(4),
  WAIT_RIDER_PICKUP(5),
  DELIVERING(6),
  COMPLETED(7),
  CANCELLED(8),
  AFTER_SALE(9);

  private final int code;

  OrderStatus(int code) {
    this.code = code;
  }

  public int code() {
    return code;
  }
}
