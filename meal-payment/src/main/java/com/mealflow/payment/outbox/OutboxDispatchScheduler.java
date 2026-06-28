package com.mealflow.payment.outbox;

import com.mealflow.payment.PaymentService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "mealflow.outbox", name = "scheduler-enabled", havingValue = "true")
public class OutboxDispatchScheduler {
  private final PaymentService paymentService;

  public OutboxDispatchScheduler(PaymentService paymentService) {
    this.paymentService = paymentService;
  }

  @Scheduled(
      initialDelayString = "${mealflow.outbox.initial-delay-ms:5000}",
      fixedDelayString = "${mealflow.outbox.fixed-delay-ms:5000}")
  public void dispatch() {
    paymentService.dispatchPendingEvents(100);
  }
}
