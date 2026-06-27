package com.mealflow.payment;

import com.mealflow.common.api.ErrorCode;
import com.mealflow.common.exception.BizException;
import com.mealflow.common.status.PaymentStatus;
import com.mealflow.infra.id.IdGenerator;
import com.mealflow.infra.idempotent.IdempotentTemplate;
import com.mealflow.payment.api.CreatePaymentRequest;
import com.mealflow.payment.api.PaymentView;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class PaymentService {
  private final IdGenerator idGenerator = new IdGenerator();
  private final IdempotentTemplate idempotentTemplate = new IdempotentTemplate();
  private final Map<Long, PaymentOrder> payments = new ConcurrentHashMap<>();

  public PaymentView create(CreatePaymentRequest request) {
    return idempotentTemplate.execute("payment:create:" + request.requestId(), () -> {
      long id = idGenerator.next("paymentOrder");
      PaymentOrder payment = new PaymentOrder(id, request.orderId(), request.amountCent(), PaymentStatus.UNPAID);
      payments.put(id, payment);
      return view(payment);
    });
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
    return view(payment);
  }

  public synchronized void close(long payOrderId) {
    PaymentOrder payment = requirePayment(payOrderId);
    if (payment.status == PaymentStatus.UNPAID || payment.status == PaymentStatus.PAYING) {
      payment.status = PaymentStatus.CLOSED;
    }
  }

  public PaymentView get(long payOrderId) {
    return view(requirePayment(payOrderId));
  }

  public List<PaymentView> list() {
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

  static class PaymentOrder {
    final long id;
    final long orderId;
    final int amountCent;
    PaymentStatus status;

    PaymentOrder(long id, long orderId, int amountCent, PaymentStatus status) {
      this.id = id;
      this.orderId = orderId;
      this.amountCent = amountCent;
      this.status = status;
    }
  }
}
