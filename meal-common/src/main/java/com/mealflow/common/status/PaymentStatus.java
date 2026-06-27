package com.mealflow.common.status;

public enum PaymentStatus implements CodeEnum {
  UNPAID(0),
  PAYING(1),
  PAID(2),
  CLOSED(3),
  REFUNDING(4),
  REFUNDED(5);

  private final int code;

  PaymentStatus(int code) {
    this.code = code;
  }

  public int code() {
    return code;
  }
}
