package com.mealflow.common.status;

public enum StockReservationStatus implements CodeEnum {
  RESERVED(1),
  RELEASED(2),
  CONFIRMED(3),
  EXPIRED(4);

  private final int code;

  StockReservationStatus(int code) {
    this.code = code;
  }

  public int code() {
    return code;
  }
}
