package com.mealflow.common.web;

import com.mealflow.common.api.ErrorCode;
import com.mealflow.common.api.Result;
import com.mealflow.common.exception.BizException;
import com.mealflow.common.trace.TraceContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Single global exception handler shared by every Spring MVC service.
 *
 * <p>Business exceptions ({@link BizException}) are mapped to stable HTTP status codes based on
 * their {@link ErrorCode}, so clients and retry logic can rely on status + code instead of parsing
 * messages. Every error response carries the current {@code X-Trace-Id} header, making it possible
 * to jump from a failing request to its full cross-service log trail.</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(BizException.class)
  public ResponseEntity<Result<Void>> handleBizException(BizException ex, HttpServletRequest request,
      HttpServletResponse response) {
    HttpStatus status = statusFor(ex.errorCode());
    attachTraceId(response);
    if (status.is5xxServerError()) {
      log.error("business failure [{}] {} {}", ex.errorCode().code(), request.getMethod(),
          request.getRequestURI(), ex);
    } else {
      log.warn("business failure [{}] {} {}: {}", ex.errorCode().code(), request.getMethod(),
          request.getRequestURI(), ex.getMessage());
    }
    return ResponseEntity.status(status).body(Result.fail(ex.errorCode()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Result<Void>> handleValidation(MethodArgumentNotValidException ex,
      HttpServletResponse response) {
    attachTraceId(response);
    FieldError fieldError = ex.getBindingResult().getFieldError();
    String message = fieldError == null ? "请求参数错误" : fieldError.getField() + " " + fieldError.getDefaultMessage();
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Result.fail(ErrorCode.BAD_REQUEST.code(), message));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Result<Void>> handleUnexpected(Exception ex, HttpServletRequest request,
      HttpServletResponse response) {
    attachTraceId(response);
    log.error("unhandled exception {} {}", request.getMethod(), request.getRequestURI(), ex);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Result.fail(ErrorCode.SYSTEM_ERROR));
  }

  private HttpStatus statusFor(ErrorCode errorCode) {
    return switch (errorCode) {
      case BAD_REQUEST -> HttpStatus.BAD_REQUEST;
      case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
      case FORBIDDEN -> HttpStatus.FORBIDDEN;
      case NOT_FOUND -> HttpStatus.NOT_FOUND;
      case IDEMPOTENT_PROCESSING, ILLEGAL_STATUS, IDEMPOTENCY_KEY_REUSED, STOCK_NOT_ENOUGH, VOUCHER_UNAVAILABLE,
          SOLD_OUT, DUPLICATE -> HttpStatus.CONFLICT;
      default -> HttpStatus.INTERNAL_SERVER_ERROR;
    };
  }

  private void attachTraceId(HttpServletResponse response) {
    String traceId = TraceContext.current();
    if (traceId != null) {
      response.setHeader(TraceContext.TRACE_ID_HEADER, traceId);
    }
  }
}
