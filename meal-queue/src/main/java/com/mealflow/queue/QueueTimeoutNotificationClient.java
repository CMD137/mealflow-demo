package com.mealflow.queue;

import com.mealflow.common.api.Result;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class QueueTimeoutNotificationClient {
  private static final String CONSUMER_GROUP = "queue-timeout-notification";

  private final RestTemplate restTemplate;
  private final String notifyBaseUrl;

  public QueueTimeoutNotificationClient(RestTemplate restTemplate,
      @Value("${mealflow.services.notify:http://meal-notify}") String notifyBaseUrl) {
    this.restTemplate = restTemplate;
    this.notifyBaseUrl = notifyBaseUrl;
  }

  public void sendTimeout(long ticketId, long userId, String ticketNo) {
    String eventKey = "queue:QueueTicketTimeout:" + ticketId + ":1";
    ConsumedPushMessageRequest request = new ConsumedPushMessageRequest(eventKey, CONSUMER_GROUP,
        new PushMessageRequest(userId, "QUEUE", "排队号 " + ticketNo + " 已超时，系统已释放本次排队容量，请重新下单。"));
    Result<?> result = restTemplate.postForObject(notifyBaseUrl + "/notify/internal/events/messages", request,
        Result.class);
    if (result == null || !result.success()) {
      throw new IllegalStateException(result == null ? "notify timeout message failed" : result.message());
    }
  }

  record ConsumedPushMessageRequest(String eventKey, String consumerGroup, PushMessageRequest message) {
  }

  record PushMessageRequest(long userId, String bizType, String content) {
  }
}
