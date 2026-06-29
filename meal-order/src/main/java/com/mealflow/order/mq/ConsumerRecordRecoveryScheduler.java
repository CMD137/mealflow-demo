package com.mealflow.order.mq;

import com.mealflow.order.OrderService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "mealflow.mq.payment-consumer.recovery", name = "enabled", havingValue = "true")
public class ConsumerRecordRecoveryScheduler {
  private final OrderService orderService;

  public ConsumerRecordRecoveryScheduler(OrderService orderService) {
    this.orderService = orderService;
  }

  @Scheduled(
      initialDelayString = "${mealflow.mq.payment-consumer.recovery.initial-delay-ms:30000}",
      fixedDelayString = "${mealflow.mq.payment-consumer.recovery.fixed-delay-ms:30000}")
  public void recover() {
    orderService.recoverTimedOutConsumerRecords();
  }
}
