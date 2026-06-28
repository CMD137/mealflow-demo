package com.mealflow.fulfillment.outbox;

import com.mealflow.fulfillment.FulfillmentService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "mealflow.outbox", name = "scheduler-enabled", havingValue = "true")
public class OutboxDispatchScheduler {
  private final FulfillmentService fulfillmentService;

  public OutboxDispatchScheduler(FulfillmentService fulfillmentService) {
    this.fulfillmentService = fulfillmentService;
  }

  @Scheduled(
      initialDelayString = "${mealflow.outbox.initial-delay-ms:5000}",
      fixedDelayString = "${mealflow.outbox.fixed-delay-ms:5000}")
  public void dispatch() {
    fulfillmentService.dispatchPendingEvents(100);
  }
}
