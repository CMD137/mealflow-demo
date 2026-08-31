package com.mealflow.queue;

import com.mealflow.queue.mapper.QueueMapper;
import com.mealflow.queue.mapper.QueueTimeoutNotificationRow;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class QueueTimeoutNotificationDispatcher {
  private static final Duration SENDING_TIMEOUT = Duration.ofMinutes(2);

  private final QueueMapper queueMapper;
  private final QueueTimeoutNotificationClient notificationClient;

  public QueueTimeoutNotificationDispatcher(QueueMapper queueMapper,
      QueueTimeoutNotificationClient notificationClient) {
    this.queueMapper = queueMapper;
    this.notificationClient = notificationClient;
  }

  @Scheduled(initialDelayString = "${mealflow.queue.timeout-notify.initial-delay-ms:5000}",
      fixedDelayString = "${mealflow.queue.timeout-notify.fixed-delay-ms:5000}")
  public void dispatchScheduled() {
    dispatchPending(100);
  }

  public int dispatchPending(int limit) {
    LocalDateTime now = LocalDateTime.now();
    queueMapper.recoverStaleTimeoutNotifications(now.minus(SENDING_TIMEOUT), now);
    int dispatched = 0;
    List<QueueTimeoutNotificationRow> notifications = queueMapper.findDispatchableTimeoutNotifications(
        Math.max(1, Math.min(limit, 100)));
    for (QueueTimeoutNotificationRow notification : notifications) {
      if (queueMapper.markTimeoutNotificationSending(notification.getTicketId(), LocalDateTime.now()) == 0) {
        continue;
      }
      try {
        notificationClient.sendTimeout(notification.getTicketId(), notification.getUserId(), notification.getTicketNo());
        queueMapper.markTimeoutNotificationSent(notification.getTicketId(), LocalDateTime.now());
        dispatched++;
      } catch (RuntimeException ex) {
        queueMapper.markTimeoutNotificationFailed(notification.getTicketId(), trimError(ex), LocalDateTime.now());
      }
    }
    return dispatched;
  }

  private String trimError(RuntimeException ex) {
    String message = ex.getMessage();
    if (message == null || message.isBlank()) {
      return ex.getClass().getSimpleName();
    }
    return message.length() <= 512 ? message : message.substring(0, 512);
  }
}
