package com.mealflow.payment;

import com.mealflow.common.api.Result;
import com.mealflow.common.api.ErrorCode;
import com.mealflow.common.exception.BizException;
import com.mealflow.common.security.RequestIdentity;
import com.mealflow.payment.api.ClosePaymentRequest;
import com.mealflow.payment.api.CreatePaymentRequest;
import com.mealflow.payment.api.LocalEventView;
import com.mealflow.payment.api.PaymentView;
import com.mealflow.payment.api.PaymentCheckoutView;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import java.util.Map;

@RestController
@RequestMapping("/payments")
public class PaymentController {
  private final PaymentService paymentService;

  public PaymentController(PaymentService paymentService) {
    this.paymentService = paymentService;
  }

  @PostMapping("/internal/create")
  public Result<PaymentView> create(@Valid @RequestBody CreatePaymentRequest request) {
    return Result.ok(paymentService.create(request));
  }

  @PostMapping("/internal/{payOrderId}/mock-pay")
  public Result<PaymentView> mockPay(@PathVariable long payOrderId) {
    return Result.ok(paymentService.mockPay(payOrderId));
  }

  @PostMapping("/internal/{payOrderId}/close")
  public Result<Void> close(@PathVariable long payOrderId, @Valid @RequestBody ClosePaymentRequest request) {
    paymentService.close(payOrderId);
    return Result.ok();
  }

  @PostMapping("/internal/{payOrderId}/refund")
  public Result<PaymentView> refund(@PathVariable long payOrderId) {
    return Result.ok(paymentService.refund(payOrderId));
  }

  @GetMapping("/{payOrderId}")
  public Result<PaymentView> get(@PathVariable long payOrderId,
      @RequestHeader(value = "X-User-Id", required = false) Long userId) {
    PaymentView payment = paymentService.get(payOrderId);
    if (payment.userId() != RequestIdentity.requireUser(userId)) {
      throw new BizException(ErrorCode.FORBIDDEN, "payment does not belong to current user");
    }
    return Result.ok(payment);
  }

  @PostMapping("/{payOrderId}/checkout")
  public Result<PaymentCheckoutView> checkout(@PathVariable long payOrderId,
      @RequestHeader(value = "X-User-Id", required = false) Long userId) {
    PaymentView payment = paymentService.get(payOrderId);
    if (payment.userId() != RequestIdentity.requireUser(userId)) {
      throw new BizException(ErrorCode.FORBIDDEN, "payment does not belong to current user");
    }
    return Result.ok(paymentService.checkout(payOrderId));
  }

  @PostMapping("/alipay/callback")
  public String alipayCallback(@RequestParam Map<String, String> parameters) {
    return paymentService.confirmAlipayCallback(parameters) ? "success" : "failure";
  }

  @GetMapping("/internal/list")
  public Result<List<PaymentView>> list() {
    return Result.ok(paymentService.list());
  }

  @GetMapping("/internal/events")
  public Result<List<LocalEventView>> events() {
    return Result.ok(paymentService.events());
  }

  @PostMapping("/internal/events/dispatch")
  public Result<Integer> dispatchEvents() {
    return Result.ok(paymentService.dispatchPendingEvents(100));
  }
}
