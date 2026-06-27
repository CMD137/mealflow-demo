package com.mealflow.common.status;

public enum CapacityTokenStatus implements CodeEnum {
  HELD(1),
  RELEASED(2),
  EXPIRED(3);

  private final int code;

  CapacityTokenStatus(int code) {
    this.code = code;
  }

  public int code() {
    return code;
  }
}
