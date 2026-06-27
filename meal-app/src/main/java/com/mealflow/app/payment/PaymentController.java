package com.mealflow.app.payment;

import com.mealflow.common.api.Result;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/payments")
public class PaymentController {
  private final PaymentService paymentService;

  public PaymentController(PaymentService paymentService) {
    this.paymentService = paymentService;
  }

  @PostMapping("/{payOrderId}/mock-pay")
  public Result<PaymentView> mockPay(@PathVariable long payOrderId) {
    return Result.ok(paymentService.mockPay(payOrderId));
  }
}
