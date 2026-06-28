package com.mealflow.payment;

import com.mealflow.common.api.Result;
import com.mealflow.payment.api.ClosePaymentRequest;
import com.mealflow.payment.api.CreatePaymentRequest;
import com.mealflow.payment.api.LocalEventView;
import com.mealflow.payment.api.PaymentView;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

  @PostMapping("/{payOrderId}/mock-pay")
  public Result<PaymentView> mockPay(@PathVariable long payOrderId) {
    return Result.ok(paymentService.mockPay(payOrderId));
  }

  @PostMapping("/{payOrderId}/close")
  public Result<Void> close(@PathVariable long payOrderId, @Valid @RequestBody ClosePaymentRequest request) {
    paymentService.close(payOrderId);
    return Result.ok();
  }

  @GetMapping("/{payOrderId}")
  public Result<PaymentView> get(@PathVariable long payOrderId) {
    return Result.ok(paymentService.get(payOrderId));
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
