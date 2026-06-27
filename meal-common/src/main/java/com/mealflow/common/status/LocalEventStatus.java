package com.mealflow.common.status;

public enum LocalEventStatus implements CodeEnum {
  NEW(0),
  SENDING(1),
  SENT(2),
  FAILED(3),
  DEAD(4);

  private final int code;

  LocalEventStatus(int code) {
    this.code = code;
  }

  public int code() {
    return code;
  }
}
