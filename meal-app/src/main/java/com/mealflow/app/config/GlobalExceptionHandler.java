package com.mealflow.app.config;

import com.mealflow.common.api.ErrorCode;
import com.mealflow.common.api.Result;
import com.mealflow.common.exception.BizException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(BizException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public Result<Void> handleBizException(BizException ex) {
    return Result.fail(ex.errorCode().code(), ex.getMessage());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public Result<Void> handleValidation(MethodArgumentNotValidException ex) {
    String message = ex.getBindingResult().getFieldErrors().stream()
        .findFirst()
        .map(error -> error.getField() + " " + error.getDefaultMessage())
        .orElse(ErrorCode.BAD_REQUEST.message());
    return Result.fail(ErrorCode.BAD_REQUEST.code(), message);
  }

  @ExceptionHandler(Exception.class)
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  public Result<Void> handleException(Exception ex) {
    return Result.fail(ErrorCode.SYSTEM_ERROR.code(), ex.getMessage());
  }
}
