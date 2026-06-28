package com.mealflow.notify;

import static org.assertj.core.api.Assertions.assertThat;

import com.mealflow.notify.api.MessageView;
import com.mealflow.notify.api.PushMessageRequest;
import com.mealflow.notify.mapper.ConsumerRecordMapper;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = "spring.cloud.nacos.discovery.enabled=false"
)
class NotifyPersistenceTest {
  @Autowired
  private NotifyService notifyService;

  @Autowired
  private ConsumerRecordMapper consumerRecordMapper;

  @Test
  void pushesAndListsMessagesInDatabase() {
    MessageView message = notifyService.push(new PushMessageRequest(101L, "ORDER", "meal ready"));

    assertThat(notifyService.list(101L))
        .anySatisfy(stored -> {
          assertThat(stored.messageId()).isEqualTo(message.messageId());
          assertThat(stored.content()).isEqualTo("meal ready");
        });
  }

  @Test
  void consumesEventOnlyOnceWithPersistentRecord() {
    MessageView created = notifyService.pushOnce("order:OrderMealReady:10001:1", "notify-message",
        new PushMessageRequest(102L, "ORDER", "order meal ready"));
    MessageView duplicate = notifyService.pushOnce("order:OrderMealReady:10001:1", "notify-message",
        new PushMessageRequest(102L, "ORDER", "order meal ready again"));

    assertThat(created).isNotNull();
    assertThat(duplicate).isNull();
    assertThat(notifyService.list(102L))
        .singleElement()
        .satisfies(stored -> assertThat(stored.content()).isEqualTo("order meal ready"));
    assertThat(notifyService.consumerRecords())
        .singleElement()
        .satisfies(record -> {
          assertThat(record.eventKey()).isEqualTo("order:OrderMealReady:10001:1");
          assertThat(record.consumerGroup()).isEqualTo("notify-message");
          assertThat(record.status()).isEqualTo("SUCCESS");
        });
  }

  @Test
  void consumesDomainEventAsNotificationOnlyOnce() {
    String payload = "{\"orderId\":10002,\"userId\":103,\"status\":\"WAIT_MERCHANT_ACCEPT\"}";

    MessageView created = notifyService.pushFromDomainEvent("order:OrderPaid:10002:1",
        "mealflow-notify-domain-consumer", "OrderPaid", payload);
    MessageView duplicate = notifyService.pushFromDomainEvent("order:OrderPaid:10002:1",
        "mealflow-notify-domain-consumer", "OrderPaid", payload);

    assertThat(created).isNotNull();
    assertThat(duplicate).isNull();
    assertThat(notifyService.list(103L))
        .singleElement()
        .satisfies(stored -> assertThat(stored.content()).isEqualTo("订单 10002 已支付，等待商户接单"));
  }

  @Test
  void retriesTimedOutProcessingConsumerRecord() {
    String eventKey = "order:OrderMealReady:10003:1";
    String consumerGroup = "notify-message-timeout";
    consumerRecordMapper.insertProcessing(consumerRecordMapper.maxRecordId() + 100, eventKey, consumerGroup,
        LocalDateTime.now().minusMinutes(10));

    MessageView retried = notifyService.pushOnce(eventKey, consumerGroup,
        new PushMessageRequest(104L, "ORDER", "meal ready after retry"));

    assertThat(retried).isNotNull();
    assertThat(notifyService.list(104L))
        .singleElement()
        .satisfies(stored -> assertThat(stored.content()).isEqualTo("meal ready after retry"));
    assertThat(consumerRecordMapper.findByEvent(eventKey, consumerGroup).getStatus()).isEqualTo("SUCCESS");
  }
}
