package com.mealflow.order.outbox;

import com.mealflow.order.OrderService;
import com.mealflow.order.OrderSagaCoordinator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "mealflow.outbox", name = "scheduler-enabled", havingValue = "true")
public class OutboxDispatchScheduler {
  private final OrderService orderService;
  private final OrderSagaCoordinator sagaCoordinator;

  public OutboxDispatchScheduler(OrderService orderService, OrderSagaCoordinator sagaCoordinator) {
    this.orderService = orderService;
    this.sagaCoordinator = sagaCoordinator;
  }

  @Scheduled(
      initialDelayString = "${mealflow.outbox.initial-delay-ms:5000}",
      fixedDelayString = "${mealflow.outbox.fixed-delay-ms:5000}")
  public void dispatch() {
    sagaCoordinator.dispatchReady(100);
    orderService.dispatchPendingEvents(100);
  }
}
