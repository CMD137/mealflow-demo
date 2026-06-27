package com.mealflow.common.api;

public record Result<T>(boolean success, String code, String message, T data) {

  public static <T> Result<T> ok(T data) {
    return new Result<>(true, "OK", "成功", data);
  }

  public static Result<Void> ok() {
    return ok(null);
  }

  public static Result<Void> fail(ErrorCode errorCode) {
    return new Result<>(false, errorCode.code(), errorCode.message(), null);
  }

  public static Result<Void> fail(String code, String message) {
    return new Result<>(false, code, message, null);
  }
}
