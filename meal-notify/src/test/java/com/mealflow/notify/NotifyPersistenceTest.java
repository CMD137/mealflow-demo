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
}
