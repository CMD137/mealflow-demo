package com.mealflow.payment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mealflow.common.api.ErrorCode;
import com.mealflow.common.exception.BizException;
import com.mealflow.common.status.LocalEventStatus;
import com.mealflow.common.status.PaymentStatus;
import com.mealflow.infra.event.EventKey;
import com.mealflow.payment.api.CreatePaymentRequest;
import com.mealflow.payment.api.LocalEventView;
import com.mealflow.payment.api.PaymentView;
import com.mealflow.payment.mapper.LocalEventMapper;
import com.mealflow.payment.mapper.LocalEventRow;
import com.mealflow.payment.mapper.PaymentMapper;
import com.mealflow.payment.mapper.PaymentOrderRow;
import com.mealflow.payment.mapper.PaymentRefundMapper;
import com.mealflow.payment.mapper.PaymentRefundRow;
import com.mealflow.payment.outbox.OutboxEventPublisher;
import com.mealflow.payment.provider.PaymentProviderPort;
import com.mealflow.payment.api.PaymentCheckoutView;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PaymentService {
  private final Duration outboxLease;
  private final int outboxMaxAttempts;

  private final PaymentDatabaseIdGenerator idGenerator;
  private final PaymentIdempotencyService idempotencyService;
  private final PaymentMapper paymentMapper;
  private final PaymentRefundMapper paymentRefundMapper;
  private final LocalEventMapper localEventMapper;
  private final OutboxEventPublisher outboxEventPublisher;
  private final ObjectMapper objectMapper;
  private final Map<String, PaymentProviderPort> paymentProviders;
  private final String provider;
  private final TransactionTemplate transactionTemplate;

  public PaymentService(PaymentMapper paymentMapper, PaymentRefundMapper paymentRefundMapper,
      LocalEventMapper localEventMapper,
      OutboxEventPublisher outboxEventPublisher, ObjectMapper objectMapper, List<PaymentProviderPort> paymentProviders,
      @org.springframework.beans.factory.annotation.Value("${mealflow.payment.provider:alipay-sandbox}") String provider,
      @org.springframework.beans.factory.annotation.Value("${mealflow.outbox.lease-seconds:60}") long outboxLeaseSeconds,
      @org.springframework.beans.factory.annotation.Value("${mealflow.outbox.max-attempts:5}") int outboxMaxAttempts,
      PaymentIdempotencyService idempotencyService, PaymentDatabaseIdGenerator idGenerator,
      PlatformTransactionManager transactionManager) {
    this.paymentMapper = paymentMapper;
    this.paymentRefundMapper = paymentRefundMapper;
    this.localEventMapper = localEventMapper;
    this.outboxEventPublisher = outboxEventPublisher;
    this.objectMapper = objectMapper;
    this.paymentProviders = paymentProviders.stream().collect(java.util.stream.Collectors.toMap(PaymentProviderPort::code,
        Function.identity()));
    this.provider = provider;
    this.outboxLease = Duration.ofSeconds(outboxLeaseSeconds);
    this.outboxMaxAttempts = outboxMaxAttempts;
    this.idempotencyService = idempotencyService;
    this.idGenerator = idGenerator;
    this.transactionTemplate = new TransactionTemplate(transactionManager);
  }

  @Transactional
  public PaymentView create(CreatePaymentRequest request) {
    return idempotencyService.execute(request, () -> {
      long id = idGenerator.next("paymentOrder");
      paymentMapper.insert(id, request.orderId(), request.userId(), provider, merchantOrderNo(id), request.amountCent(), PaymentStatus.UNPAID.name(),
          LocalDateTime.now());
      return requirePayment(id);
    });
  }

  @Transactional
  public PaymentView mockPay(long payOrderId) {
    PaymentView payment = requirePayment(payOrderId);
    PaymentStatus status = PaymentStatus.valueOf(payment.status());
    if (status == PaymentStatus.PAID) {
      return payment;
    }
    if (status != PaymentStatus.UNPAID && status != PaymentStatus.PAYING) {
      throw new BizException(ErrorCode.ILLEGAL_STATUS, "payment order is not payable");
    }
    int updated = paymentMapper.updatePayableStatus(payOrderId, PaymentStatus.PAID.name(), PaymentStatus.UNPAID.name(),
        PaymentStatus.PAYING.name(), LocalDateTime.now());
    if (updated > 0) {
      PaymentView paid = requirePayment(payOrderId);
      appendPaymentPaidEvent(paid);
      return paid;
    }
    return requirePayment(payOrderId);
  }

  @Transactional
  public void close(long payOrderId) {
    requirePayment(payOrderId);
    paymentMapper.updatePayableStatus(payOrderId, PaymentStatus.CLOSED.name(), PaymentStatus.UNPAID.name(),
        PaymentStatus.PAYING.name(), LocalDateTime.now());
  }

  public synchronized PaymentView refund(long payOrderId) {
    PaymentOrderRow payment = requirePaymentRow(payOrderId);
    PaymentStatus status = PaymentStatus.valueOf(payment.getStatus());
    if (status == PaymentStatus.REFUNDED) {
      return view(payment);
    }
    if (status != PaymentStatus.PAID && status != PaymentStatus.REFUNDING) {
      throw new BizException(ErrorCode.ILLEGAL_STATUS, "payment order is not refundable");
    }
    prepareRefund(payment);
    PaymentRefundRow refund = paymentRefundMapper.findByPayOrderId(payOrderId);
    PaymentProviderPort adapter = requireProvider(refund.getProvider());
    try {
      PaymentProviderPort.RefundResult result = adapter.refund(refund.getMerchantOrderNo(),
          refund.getRefundRequestNo(), refund.getAmountCent());
      if (result.successful()) {
        completeRefund(refund, result);
        return requirePayment(payOrderId);
      }
      throw new IllegalStateException(result.processing() ? "refund is processing" : "refund rejected: " + result.message());
    } catch (RuntimeException ex) {
      recordPending(refund, null, trimError(ex));
      throw ex;
    }
  }

  public int queryPendingRefunds(int limit) {
    int completed = 0;
    for (PaymentRefundRow refund : paymentRefundMapper.findDue(LocalDateTime.now(), limit)) {
      try {
        PaymentProviderPort.RefundResult result = requireProvider(refund.getProvider())
            .queryRefund(refund.getMerchantOrderNo(), refund.getRefundRequestNo());
        if (result.successful()) {
          completeRefund(refund, result);
          completed++;
        } else {
          recordPending(refund, result.rawResponse(), result.message());
        }
      } catch (RuntimeException ex) {
        recordPending(refund, null, trimError(ex));
      }
    }
    return completed;
  }

  public PaymentView get(long payOrderId) {
    return requirePayment(payOrderId);
  }

  public PaymentCheckoutView checkout(long payOrderId) {
    PaymentView payment = requirePayment(payOrderId);
    if (PaymentStatus.valueOf(payment.status()) != PaymentStatus.UNPAID) {
      throw new BizException(ErrorCode.ILLEGAL_STATUS, "payment order is not payable");
    }
    PaymentProviderPort adapter = paymentProviders.get(provider);
    if (adapter == null) {
      throw new IllegalStateException("unsupported payment provider: " + provider);
    }
    return new PaymentCheckoutView(payOrderId, provider, adapter.checkoutUrl(payOrderId, payment.amountCent()));
  }

  @Transactional
  public boolean confirmAlipayCallback(Map<String, String> parameters) {
    PaymentProviderPort adapter = paymentProviders.get("alipay-sandbox");
    if (adapter == null || !adapter.verifyCallback(parameters)
        || !("TRADE_SUCCESS".equals(parameters.get("trade_status")) || "TRADE_FINISHED".equals(parameters.get("trade_status")))) {
      return false;
    }
    String merchantOrderNo = parameters.get("out_trade_no");
    if (merchantOrderNo == null || !merchantOrderNo.matches("MF\\d+")) {
      return false;
    }
    PaymentView payment = requirePayment(Long.parseLong(merchantOrderNo.substring(2)));
    if (!amountMatches(payment.amountCent(), parameters.get("total_amount"))) {
      return false;
    }
    paymentMapper.recordCallback(payment.payOrderId(), parameters.get("trade_no"), callbackDigest(parameters),
        parameters.get("trade_status"), LocalDateTime.now());
    mockPay(payment.payOrderId());
    return true;
  }

  public List<PaymentView> list() {
    return paymentMapper.findAll().stream().map(this::view).toList();
  }

  public List<LocalEventView> events() {
    return localEventMapper.findAll().stream().map(this::eventView).toList();
  }

  public int dispatchPendingEvents(int limit) {
    recoverStaleSendingEvents();
    int sent = 0;
    LocalDateTime now = LocalDateTime.now();
    for (LocalEventRow row : localEventMapper.findDispatchable(now, limit)) {
      if (localEventMapper.markSending(row.getId(), now, now.plus(outboxLease)) == 0) {
        continue;
      }
      try {
        outboxEventPublisher.publish(eventView(row));
        localEventMapper.markSent(row.getId(), LocalDateTime.now());
        sent++;
      } catch (RuntimeException ex) {
        LocalDateTime failedAt = LocalDateTime.now();
        localEventMapper.markFailed(row.getId(), trimError(ex), failedAt, retryAt(row.getRetryCount() + 1, failedAt), outboxMaxAttempts);
      }
    }
    return sent;
  }

  public int recoverStaleSendingEvents() {
    LocalDateTime now = LocalDateTime.now();
    return localEventMapper.markExpiredLeases(now, now.minusSeconds(1), outboxMaxAttempts);
  }

  private PaymentView requirePayment(long payOrderId) {
    return view(requirePaymentRow(payOrderId));
  }

  private PaymentOrderRow requirePaymentRow(long payOrderId) {
    PaymentOrderRow payment = paymentMapper.findById(payOrderId);
    if (payment == null) {
      throw new BizException(ErrorCode.NOT_FOUND, "payment order not found");
    }
    return payment;
  }

  private void prepareRefund(PaymentOrderRow payment) {
    transactionTemplate.executeWithoutResult(status -> {
      LocalDateTime now = LocalDateTime.now();
      PaymentOrderRow current = requirePaymentRow(payment.getId());
      PaymentStatus currentStatus = PaymentStatus.valueOf(current.getStatus());
      if (currentStatus == PaymentStatus.PAID) {
        paymentMapper.markRefunding(current.getId(), PaymentStatus.PAID.name(), PaymentStatus.REFUNDING.name(), now);
      } else if (currentStatus != PaymentStatus.REFUNDING && currentStatus != PaymentStatus.REFUNDED) {
        throw new BizException(ErrorCode.ILLEGAL_STATUS, "payment order is not refundable");
      }
      paymentRefundMapper.insert(idGenerator.next("paymentRefund"), current.getId(), current.getProvider(),
          current.getMerchantOrderNo(), "MFR" + current.getId(), current.getAmountCent(), now);
    });
  }

  private void completeRefund(PaymentRefundRow refund, PaymentProviderPort.RefundResult result) {
    transactionTemplate.executeWithoutResult(status -> {
      LocalDateTime now = LocalDateTime.now();
      paymentRefundMapper.markSuccess(refund.getId(), result.channelTransactionNo(), result.channelRefundNo(),
          result.rawResponse(), now);
      paymentMapper.completeRefund(refund.getPayOrderId(), PaymentStatus.REFUNDING.name(),
          PaymentStatus.REFUNDED.name(), now);
    });
  }

  private void recordPending(PaymentRefundRow refund, String rawResponse, String error) {
    LocalDateTime now = LocalDateTime.now();
    paymentRefundMapper.recordPending(refund.getId(), rawResponse, error, retryAt(refund.getRetryCount() + 1, now), now);
  }

  private PaymentProviderPort requireProvider(String providerCode) {
    PaymentProviderPort adapter = paymentProviders.get(providerCode);
    if (adapter == null) {
      throw new IllegalStateException("unsupported payment provider: " + providerCode);
    }
    return adapter;
  }

  private PaymentView view(PaymentOrderRow payment) {
    return new PaymentView(payment.getId(), payment.getOrderId(), payment.getUserId(), payment.getAmountCent(), payment.getStatus());
  }

  private void appendPaymentPaidEvent(PaymentView payment) {
    String eventType = "PaymentPaid";
    int version = 1;
    localEventMapper.insert(idGenerator.next("paymentLocalEvent"),
        EventKey.of("payment", eventType, payment.payOrderId(), version),
        eventType,
        version,
        "PAYMENT_ORDER",
        payment.payOrderId(),
        toJson(Map.of(
            "payOrderId", payment.payOrderId(),
            "orderId", payment.orderId(),
            "amountCent", payment.amountCent(),
            "status", payment.status())),
        LocalEventStatus.NEW.name(),
        LocalDateTime.now());
  }

  private LocalEventView eventView(LocalEventRow row) {
    return new LocalEventView(row.getId(), row.getEventKey(), row.getEventType(), row.getEventVersion(),
        row.getAggregateType(), row.getAggregateId(), row.getPayloadJson(), row.getStatus(), row.getRetryCount(),
        row.getLastError(), row.getCreateTime(), row.getUpdateTime());
  }

  private String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("failed to serialize payment event", e);
    }
  }

  private String trimError(RuntimeException ex) {
    String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
    return message.length() <= 512 ? message : message.substring(0, 512);
  }

  private boolean amountMatches(int amountCent, String callbackAmount) {
    try {
      return java.math.BigDecimal.valueOf(amountCent, 2).compareTo(new java.math.BigDecimal(callbackAmount)) == 0;
    } catch (NumberFormatException ex) {
      return false;
    }
  }

  private String merchantOrderNo(long payOrderId) { return "MF" + payOrderId; }

  private String callbackDigest(Map<String, String> parameters) {
    try {
      String canonical = parameters.entrySet().stream().sorted(Map.Entry.comparingByKey())
          .map(entry -> entry.getKey() + "=" + entry.getValue()).collect(java.util.stream.Collectors.joining("&"));
      return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
          .digest(canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException ex) {
      throw new IllegalStateException(ex);
    }
  }

  private LocalDateTime retryAt(int attempt, LocalDateTime now) {
    long seconds = Math.min(300, 1L << Math.min(8, Math.max(0, attempt)));
    return now.plusSeconds(seconds);
  }
}
