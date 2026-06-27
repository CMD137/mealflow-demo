package com.mealflow.app.payment;

import com.mealflow.common.api.ErrorCode;
import com.mealflow.common.exception.BizException;
import com.mealflow.common.status.PaymentStatus;
import com.mealflow.infra.id.IdGenerator;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class PaymentService {
  private final IdGenerator idGenerator;
  private final ApplicationEventPublisher eventPublisher;
  private final Map<Long, PaymentOrder> payments = new ConcurrentHashMap<>();

  public PaymentService(IdGenerator idGenerator, ApplicationEventPublisher eventPublisher) {
    this.idGenerator = idGenerator;
    this.eventPublisher = eventPublisher;
  }

  public PaymentOrder createForOrder(long orderId, int amountCent) {
    long payOrderId = idGenerator.next("paymentOrder");
    PaymentOrder payment = new PaymentOrder(payOrderId, orderId, amountCent, PaymentStatus.UNPAID);
    payments.put(payOrderId, payment);
    return payment;
  }

  public synchronized PaymentView mockPay(long payOrderId) {
    PaymentOrder payment = requirePayment(payOrderId);
    if (payment.status == PaymentStatus.PAID) {
      return view(payment);
    }
    if (payment.status != PaymentStatus.UNPAID && payment.status != PaymentStatus.PAYING) {
      throw new BizException(ErrorCode.ILLEGAL_STATUS, "支付单当前不可支付");
    }
    payment.status = PaymentStatus.PAID;
    eventPublisher.publishEvent(new PaymentSuccessEvent(payment.id, payment.orderId));
    return view(payment);
  }

  public synchronized void close(long payOrderId) {
    PaymentOrder payment = requirePayment(payOrderId);
    if (payment.status == PaymentStatus.UNPAID || payment.status == PaymentStatus.PAYING) {
      payment.status = PaymentStatus.CLOSED;
    }
  }

  public List<PaymentView> payments() {
    return payments.values().stream()
        .sorted(Comparator.comparingLong(payment -> payment.id))
        .map(this::view)
        .toList();
  }

  private PaymentOrder requirePayment(long payOrderId) {
    PaymentOrder payment = payments.get(payOrderId);
    if (payment == null) {
      throw new BizException(ErrorCode.NOT_FOUND, "支付单不存在");
    }
    return payment;
  }

  private PaymentView view(PaymentOrder payment) {
    return new PaymentView(payment.id, payment.orderId, payment.amountCent, payment.status.name());
  }

  public static class PaymentOrder {
    public final long id;
    public final long orderId;
    public final int amountCent;
    public PaymentStatus status;

    PaymentOrder(long id, long orderId, int amountCent, PaymentStatus status) {
      this.id = id;
      this.orderId = orderId;
      this.amountCent = amountCent;
      this.status = status;
    }
  }
}
