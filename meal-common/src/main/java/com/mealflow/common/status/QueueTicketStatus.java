package com.mealflow.common.status;

public enum QueueTicketStatus implements CodeEnum {
  WAITING(1),
  READY(2),
  PROCESSING(3),
  ORDER_CREATED(4),
  CANCELLED(5),
  TIMEOUT(6),
  FAILED(7);

  private final int code;

  QueueTicketStatus(int code) {
    this.code = code;
  }

  public int code() {
    return code;
  }
}
