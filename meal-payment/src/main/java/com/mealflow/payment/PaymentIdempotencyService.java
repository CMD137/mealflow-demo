package com.mealflow.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mealflow.common.api.ErrorCode;
import com.mealflow.common.exception.BizException;
import com.mealflow.payment.api.CreatePaymentRequest;
import com.mealflow.payment.api.PaymentView;
import com.mealflow.payment.mapper.PaymentIdempotencyMapper;
import com.mealflow.payment.mapper.PaymentIdempotencyRow;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;

@Service
public class PaymentIdempotencyService {
  private final PaymentIdempotencyMapper mapper;
  private final ObjectMapper objectMapper;
  public PaymentIdempotencyService(PaymentIdempotencyMapper mapper, ObjectMapper objectMapper) { this.mapper = mapper; this.objectMapper = objectMapper; }

  public PaymentView execute(CreatePaymentRequest request, Supplier<PaymentView> action) {
    String subject = "order:" + request.orderId();
    String hash = hash(request);
    LocalDateTime now = LocalDateTime.now();
    if (mapper.insertProcessing(subject, request.requestId(), hash, now.plusMinutes(1), now) == 1) return run(subject, request.requestId(), action);
    PaymentIdempotencyRow row = mapper.find(subject, request.requestId());
    if (row == null) throw new BizException(ErrorCode.IDEMPOTENT_PROCESSING);
    if (!hash.equals(row.getRequestHash())) throw new BizException(ErrorCode.IDEMPOTENCY_KEY_REUSED, "idempotency key belongs to a different request");
    if ("SUCCESS".equals(row.getStatus())) return read(row.getResponseJson());
    if (row.getLeaseExpireTime() != null && row.getLeaseExpireTime().isBefore(now)
        && mapper.takeOver(subject, request.requestId(), now.plusMinutes(1), now) == 1) return run(subject, request.requestId(), action);
    throw new BizException(ErrorCode.IDEMPOTENT_PROCESSING);
  }

  private PaymentView run(String subject, String key, Supplier<PaymentView> action) {
    try { PaymentView result = action.get(); mapper.complete(subject, key, objectMapper.writeValueAsString(result), LocalDateTime.now()); return result; }
    catch (RuntimeException ex) { mapper.fail(subject, key, LocalDateTime.now()); throw ex; }
    catch (Exception ex) { throw new IllegalStateException(ex); }
  }
  private PaymentView read(String value) { try { return objectMapper.readValue(value, PaymentView.class); } catch (Exception ex) { throw new IllegalStateException(ex); } }
  private String hash(CreatePaymentRequest request) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(objectMapper.writeValueAsBytes(request))); } catch (Exception ex) { throw new IllegalStateException(ex); } }
}
