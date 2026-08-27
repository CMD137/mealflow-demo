package com.mealflow.common.status;

public enum VoucherClaimStatus implements CodeEnum {
  PROCESSING(1),
  CLAIMED(2),
  SOLD_OUT(3);

  private final int code;

  VoucherClaimStatus(int code) {
    this.code = code;
  }

  public int code() {
    return code;
  }
}
