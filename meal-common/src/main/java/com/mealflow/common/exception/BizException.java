package com.mealflow.common.exception;

import com.mealflow.common.api.ErrorCode;

public class BizException extends RuntimeException {
  private final ErrorCode errorCode;

  public BizException(ErrorCode errorCode) {
    super(errorCode.message());
    this.errorCode = errorCode;
  }

  public BizException(ErrorCode errorCode, String message) {
    super(message);
    this.errorCode = errorCode;
  }

  public ErrorCode errorCode() {
    return errorCode;
  }
}
