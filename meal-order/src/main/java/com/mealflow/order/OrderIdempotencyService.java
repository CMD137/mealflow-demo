package com.mealflow.order;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mealflow.common.api.ErrorCode;
import com.mealflow.common.exception.BizException;
import com.mealflow.order.api.SubmitOrderResponse;
import com.mealflow.order.mapper.IdempotencyRecordRow;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;

@Service
public class OrderIdempotencyService {
  private static final int LEASE_SECONDS = 60;
  private final OrderIdempotencyRecordService recordService;
  private final ObjectMapper objectMapper;

  public OrderIdempotencyService(OrderIdempotencyRecordService recordService, ObjectMapper objectMapper) {
    this.recordService = recordService;
    this.objectMapper = objectMapper;
  }

  public SubmitOrderResponse execute(long userId, String key, Object request, Supplier<SubmitOrderResponse> action) {
    String subject = "user:" + userId;
    String requestHash = hash(request);
    LocalDateTime now = LocalDateTime.now();
    if (recordService.start(subject, key, requestHash, now.plusSeconds(LEASE_SECONDS), now)) {
      return run(subject, key, action);
    }
    IdempotencyRecordRow existing = recordService.find(subject, key);
    if (existing == null) {
      throw new BizException(ErrorCode.IDEMPOTENT_PROCESSING);
    }
    if (!requestHash.equals(existing.getRequestHash())) {
      throw new BizException(ErrorCode.IDEMPOTENCY_KEY_REUSED, "idempotency key belongs to a different request");
    }
    if ("SUCCESS".equals(existing.getStatus())) {
      return fromJson(existing.getResponseJson());
    }
    if (existing.getLeaseExpireTime() != null && existing.getLeaseExpireTime().isBefore(now)
        && recordService.takeOverExpired(subject, key, now.plusSeconds(LEASE_SECONDS), now)) {
      return run(subject, key, action);
    }
    throw new BizException(ErrorCode.IDEMPOTENT_PROCESSING);
  }

  private SubmitOrderResponse run(String subject, String key, Supplier<SubmitOrderResponse> action) {
    try {
      SubmitOrderResponse response = action.get();
      recordService.complete(subject, key, toJson(response), LocalDateTime.now());
      return response;
    } catch (RuntimeException ex) {
      recordService.markFailed(subject, key, LocalDateTime.now());
      throw ex;
    }
  }

  private String hash(Object request) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(toJson(request).getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException ex) {
      throw new IllegalStateException(ex);
    }
  }

  private SubmitOrderResponse fromJson(String value) {
    try {
      return objectMapper.readValue(value, SubmitOrderResponse.class);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("failed to read idempotent response", ex);
    }
  }

  private String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("failed to serialize idempotent request", ex);
    }
  }
}
