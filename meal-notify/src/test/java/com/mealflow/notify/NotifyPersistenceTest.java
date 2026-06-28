package com.mealflow.notify;

import static org.assertj.core.api.Assertions.assertThat;

import com.mealflow.notify.api.MessageView;
import com.mealflow.notify.api.PushMessageRequest;
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
}
