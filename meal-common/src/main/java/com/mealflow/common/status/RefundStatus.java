package com.mealflow.common.status;

public enum RefundStatus implements CodeEnum {
  NONE(0),
  REFUND_REQUESTED(1),
  REFUNDING(2),
  REFUNDED(3),
  REFUND_FAILED(4);

  private final int code;

  RefundStatus(int code) {
    this.code = code;
  }

  public int code() {
    return code;
  }
}
