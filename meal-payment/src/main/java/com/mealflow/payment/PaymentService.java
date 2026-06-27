package com.mealflow.payment;

import com.mealflow.common.api.ErrorCode;
import com.mealflow.common.exception.BizException;
import com.mealflow.common.status.PaymentStatus;
import com.mealflow.infra.id.IdGenerator;
import com.mealflow.infra.idempotent.IdempotentTemplate;
import com.mealflow.payment.api.CreatePaymentRequest;
import com.mealflow.payment.api.PaymentView;
import com.mealflow.payment.mapper.PaymentMapper;
import com.mealflow.payment.mapper.PaymentOrderRow;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {
  private final IdGenerator idGenerator = new IdGenerator();
  private final IdempotentTemplate idempotentTemplate = new IdempotentTemplate();
  private final PaymentMapper paymentMapper;

  public PaymentService(PaymentMapper paymentMapper) {
    this.paymentMapper = paymentMapper;
  }

  @Transactional
  public PaymentView create(CreatePaymentRequest request) {
    return idempotentTemplate.execute("payment:create:" + request.requestId(), () -> {
      long id = idGenerator.next("paymentOrder");
      paymentMapper.insert(id, request.orderId(), request.amountCent(), PaymentStatus.UNPAID.name(),
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
    paymentMapper.updatePayableStatus(payOrderId, PaymentStatus.PAID.name(), PaymentStatus.UNPAID.name(),
        PaymentStatus.PAYING.name(), LocalDateTime.now());
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

  public List<PaymentView> list() {
    return paymentMapper.findAll().stream().map(this::view).toList();
  }

  private PaymentView requirePayment(long payOrderId) {
    PaymentOrderRow payment = paymentMapper.findById(payOrderId);
    if (payment == null) {
      throw new BizException(ErrorCode.NOT_FOUND, "payment order not found");
    }
    return view(payment);
  }

  private PaymentView view(PaymentOrderRow payment) {
    return new PaymentView(payment.getId(), payment.getOrderId(), payment.getAmountCent(), payment.getStatus());
  }
}
