package com.mealflow.payment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mealflow.common.api.ErrorCode;
import com.mealflow.common.exception.BizException;
import com.mealflow.common.status.LocalEventStatus;
import com.mealflow.common.status.PaymentStatus;
import com.mealflow.infra.event.EventKey;
import com.mealflow.infra.id.IdGenerator;
import com.mealflow.infra.idempotent.IdempotentTemplate;
import com.mealflow.payment.api.CreatePaymentRequest;
import com.mealflow.payment.api.LocalEventView;
import com.mealflow.payment.api.PaymentView;
import com.mealflow.payment.mapper.LocalEventMapper;
import com.mealflow.payment.mapper.LocalEventRow;
import com.mealflow.payment.mapper.PaymentMapper;
import com.mealflow.payment.mapper.PaymentOrderRow;
import com.mealflow.payment.outbox.OutboxEventPublisher;
import com.mealflow.payment.provider.PaymentProviderPort;
import com.mealflow.payment.api.PaymentCheckoutView;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {
  private static final Duration OUTBOX_SENDING_TIMEOUT = Duration.ofMinutes(1);

  private final IdGenerator idGenerator = new IdGenerator();
  private final IdempotentTemplate idempotentTemplate = new IdempotentTemplate();
  private final PaymentMapper paymentMapper;
  private final LocalEventMapper localEventMapper;
  private final OutboxEventPublisher outboxEventPublisher;
  private final ObjectMapper objectMapper;
  private final Map<String, PaymentProviderPort> paymentProviders;
  private final String provider;

  public PaymentService(PaymentMapper paymentMapper, LocalEventMapper localEventMapper,
      OutboxEventPublisher outboxEventPublisher, ObjectMapper objectMapper, List<PaymentProviderPort> paymentProviders,
      @org.springframework.beans.factory.annotation.Value("${mealflow.payment.provider:mock-wechat}") String provider) {
    this.paymentMapper = paymentMapper;
    this.localEventMapper = localEventMapper;
    this.outboxEventPublisher = outboxEventPublisher;
    this.objectMapper = objectMapper;
    this.paymentProviders = paymentProviders.stream().collect(java.util.stream.Collectors.toMap(PaymentProviderPort::code,
        Function.identity()));
    this.provider = provider;
  }

  @PostConstruct
  void initializeIdGenerator() {
    idGenerator.ensureAtLeast("paymentOrder", paymentMapper.maxPaymentOrderId());
    idGenerator.ensureAtLeast("localEvent", localEventMapper.maxEventId());
  }

  @Transactional
  public PaymentView create(CreatePaymentRequest request) {
    return idempotentTemplate.execute("payment:create:" + request.requestId(), () -> {
      long id = idGenerator.next("paymentOrder");
      paymentMapper.insert(id, request.orderId(), request.userId(), request.amountCent(), PaymentStatus.UNPAID.name(),
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
    for (LocalEventRow row : localEventMapper.findDispatchable(limit)) {
      if (localEventMapper.markSending(row.getId(), LocalDateTime.now()) == 0) {
        continue;
      }
      try {
        outboxEventPublisher.publish(eventView(row));
        localEventMapper.markSent(row.getId(), LocalDateTime.now());
        sent++;
      } catch (RuntimeException ex) {
        localEventMapper.markFailed(row.getId(), trimError(ex), LocalDateTime.now());
      }
    }
    return sent;
  }

  public int recoverStaleSendingEvents() {
    LocalDateTime now = LocalDateTime.now();
    return localEventMapper.markStaleSendingFailedBefore(now.minus(OUTBOX_SENDING_TIMEOUT), now);
  }

  private PaymentView requirePayment(long payOrderId) {
    PaymentOrderRow payment = paymentMapper.findById(payOrderId);
    if (payment == null) {
      throw new BizException(ErrorCode.NOT_FOUND, "payment order not found");
    }
    return view(payment);
  }

  private PaymentView view(PaymentOrderRow payment) {
    return new PaymentView(payment.getId(), payment.getOrderId(), payment.getUserId(), payment.getAmountCent(), payment.getStatus());
  }

  private void appendPaymentPaidEvent(PaymentView payment) {
    String eventType = "PaymentPaid";
    int version = 1;
    localEventMapper.insert(idGenerator.next("localEvent"),
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
}
