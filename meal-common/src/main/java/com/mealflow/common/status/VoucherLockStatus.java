package com.mealflow.common.status;

public enum VoucherLockStatus implements CodeEnum {
  LOCKED(1),
  RELEASED(2),
  CONFIRMED(3),
  EXPIRED(4);

  private final int code;

  VoucherLockStatus(int code) {
    this.code = code;
  }

  public int code() {
    return code;
  }
}
