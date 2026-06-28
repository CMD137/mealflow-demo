package com.mealflow.order.outbox;

import com.mealflow.infra.event.RocketMqOutboxClient;
import com.mealflow.order.api.LocalEventView;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "mealflow.outbox", name = "publisher", havingValue = "rocketmq")
public class RocketMqOutboxEventPublisher implements OutboxEventPublisher {
  private final RocketMqOutboxClient client;

  public RocketMqOutboxEventPublisher(
      @Value("${rocketmq.name-server}") String nameServerAddress,
      @Value("${rocketmq.producer.group:mealflow-order-producer}") String producerGroup,
      @Value("${mealflow.outbox.topic:mealflow-order-events}") String topic) {
    this.client = new RocketMqOutboxClient(producerGroup, nameServerAddress, topic);
  }

  @Override
  public void publish(LocalEventView event) {
    client.publish(event.eventKey(), event.eventType(), event.payloadJson());
  }

  @PreDestroy
  public void close() {
    client.close();
  }
}
