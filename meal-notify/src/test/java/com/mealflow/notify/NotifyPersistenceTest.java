package com.mealflow.notify;

import static org.assertj.core.api.Assertions.assertThat;

import com.mealflow.notify.api.DeliveryView;
import com.mealflow.notify.api.MessageView;
import com.mealflow.notify.api.PushMessageRequest;
import com.mealflow.notify.api.TemplateMessageRequest;
import com.mealflow.notify.mapper.ConsumerRecordMapper;
import java.time.LocalDateTime;
import java.util.Map;
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
  private NotifyStreamService notifyStreamService;

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
  void pushesTemplateToMultipleChannels() {
    MessageView message = notifyService.pushTemplated("ORDER_PAID",
        new TemplateMessageRequest(106L, Map.of("orderId", "30001"), "13800003001"));

    assertThat(message.content()).isEqualTo("Order 30001 has been paid and is waiting for merchant acceptance.");
    assertThat(notifyService.list(106L))
        .singleElement()
        .satisfies(stored -> assertThat(stored.messageId()).isEqualTo(message.messageId()));
    assertThat(notifyService.deliveries(106L))
        .extracting(DeliveryView::channel)
        .containsExactlyInAnyOrder("IN_APP", "SMS_MOCK");
    assertThat(notifyService.deliveries(106L))
        .anySatisfy(delivery -> {
          assertThat(delivery.channel()).isEqualTo("SMS_MOCK");
          assertThat(delivery.target()).isEqualTo("13800003001");
          assertThat(delivery.status()).isEqualTo("SENT");
        });
  }

  @Test
  void registersAndRemovesSseConnections() {
    notifyStreamService.subscribe(105L);

    assertThat(notifyStreamService.activeConnections(105L)).isEqualTo(1);

    notifyStreamService.disconnect(105L);

    assertThat(notifyStreamService.activeConnections(105L)).isZero();
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
    assertThat(consumerRecordMapper.findByEvent("order:OrderPaid:10002:1", "mealflow-notify-domain-consumer"))
        .satisfies(record -> {
          assertThat(record.getEventType()).isEqualTo("OrderPaid");
          assertThat(record.getPayloadJson()).isEqualTo(payload);
        });
  }

  @Test
  void retriesTimedOutProcessingConsumerRecord() {
    String eventKey = "order:OrderMealReady:10003:1";
    String consumerGroup = "notify-message-timeout";
    consumerRecordMapper.insertProcessing(consumerRecordMapper.maxRecordId() + 100, eventKey, consumerGroup,
        null, null, LocalDateTime.now().minusMinutes(10));

    MessageView retried = notifyService.pushOnce(eventKey, consumerGroup,
        new PushMessageRequest(104L, "ORDER", "meal ready after retry"));

    assertThat(retried).isNotNull();
    assertThat(notifyService.list(104L))
        .singleElement()
        .satisfies(stored -> assertThat(stored.content()).isEqualTo("meal ready after retry"));
    assertThat(consumerRecordMapper.findByEvent(eventKey, consumerGroup).getStatus()).isEqualTo("SUCCESS");
  }

  @Test
  void recoversTimedOutProcessingConsumerRecord() {
    String eventKey = "order:OrderPaid:10004:1";
    String consumerGroup = "notify-recovery-test";
    consumerRecordMapper.insertProcessing(consumerRecordMapper.maxRecordId() + 200, eventKey, consumerGroup,
        "OrderPaid", "{\"orderId\":10004,\"userId\":104}", LocalDateTime.now().minusMinutes(10));

    int recovered = notifyService.recoverTimedOutConsumerRecords();

    assertThat(recovered).isGreaterThanOrEqualTo(1);
    assertThat(consumerRecordMapper.findByEvent(eventKey, consumerGroup).getStatus()).isEqualTo("TIMEOUT");
  }

  @Test
  void replaysTimedOutDomainConsumerRecordFromStoredPayload() {
    String eventKey = "order:OrderMealReady:10005:1";
    String consumerGroup = "notify-replay-test";
    String payload = "{\"orderId\":10005,\"userId\":105,\"status\":\"WAIT_RIDER_PICKUP\"}";
    consumerRecordMapper.insertProcessing(consumerRecordMapper.maxRecordId() + 300, eventKey, consumerGroup,
        "OrderMealReady", payload, LocalDateTime.now().minusMinutes(10));

    MessageView replayed = notifyService.replayDomainConsumerRecord(eventKey, consumerGroup);

    assertThat(replayed).isNotNull();
    assertThat(replayed.content()).isEqualTo("订单 10005 已出餐，等待骑手取餐");
    assertThat(notifyService.list(105L))
        .singleElement()
        .satisfies(stored -> assertThat(stored.messageId()).isEqualTo(replayed.messageId()));
    assertThat(consumerRecordMapper.findByEvent(eventKey, consumerGroup))
        .satisfies(record -> {
          assertThat(record.getStatus()).isEqualTo("SUCCESS");
          assertThat(record.getEventType()).isEqualTo("OrderMealReady");
          assertThat(record.getPayloadJson()).isEqualTo(payload);
        });
  }
}
