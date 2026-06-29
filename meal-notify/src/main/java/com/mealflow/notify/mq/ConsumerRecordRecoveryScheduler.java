package com.mealflow.notify.mq;

import com.mealflow.notify.NotifyService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "mealflow.mq.notify-consumer.recovery", name = "enabled", havingValue = "true")
public class ConsumerRecordRecoveryScheduler {
  private final NotifyService notifyService;

  public ConsumerRecordRecoveryScheduler(NotifyService notifyService) {
    this.notifyService = notifyService;
  }

  @Scheduled(
      initialDelayString = "${mealflow.mq.notify-consumer.recovery.initial-delay-ms:30000}",
      fixedDelayString = "${mealflow.mq.notify-consumer.recovery.fixed-delay-ms:30000}")
  public void recover() {
    notifyService.recoverTimedOutConsumerRecords();
  }
}
