package com.mealflow.common.status;

public enum IdempotentRequestStatus implements CodeEnum {
  PROCESSING(0),
  SUCCESS(1),
  FAILED(2),
  EXPIRED(3);

  private final int code;

  IdempotentRequestStatus(int code) {
    this.code = code;
  }

  public int code() {
    return code;
  }
}
