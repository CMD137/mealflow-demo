package com.mealflow.common.status;

public enum ConsumerRecordStatus implements CodeEnum {
  PROCESSING(0),
  SUCCESS(1),
  FAILED(2),
  TIMEOUT(3);

  private final int code;

  ConsumerRecordStatus(int code) {
    this.code = code;
  }

  public int code() {
    return code;
  }
}
