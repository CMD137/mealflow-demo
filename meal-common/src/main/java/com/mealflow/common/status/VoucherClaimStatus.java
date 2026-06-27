package com.mealflow.common.status;

public enum VoucherClaimStatus implements CodeEnum {
  ACCEPTED(1),
  CLAIMED(2),
  DUPLICATE(3),
  FAILED(4),
  COMPENSATING(5),
  COMPENSATED(6);

  private final int code;

  VoucherClaimStatus(int code) {
    this.code = code;
  }

  public int code() {
    return code;
  }
}
