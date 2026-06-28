package com.mealflow.notify.mq;

import com.mealflow.infra.event.RocketMqConsumerClient;
import com.mealflow.infra.event.RocketMqConsumerClient.RocketMqEventMessage;
import com.mealflow.notify.NotifyService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "mealflow.mq.notify-consumer", name = "enabled", havingValue = "true")
public class DomainEventRocketMqConsumer {
  private final NotifyService notifyService;
  private final RocketMqConsumerClient client;
  private final String consumerGroup;

  public DomainEventRocketMqConsumer(
      NotifyService notifyService,
      @Value("${rocketmq.name-server}") String nameServerAddress,
      @Value("${mealflow.mq.notify-consumer.group:mealflow-notify-domain-consumer}") String consumerGroup,
      @Value("${mealflow.mq.notify-consumer.topics:mealflow-order-events}")
      String topics,
      @Value("${mealflow.mq.notify-consumer.max-reconsume-times:16}") int maxReconsumeTimes) {
    this.notifyService = notifyService;
    this.consumerGroup = consumerGroup;
    this.client = new RocketMqConsumerClient(consumerGroup, nameServerAddress, Arrays.asList(topics.split(",")),
        this::consume, maxReconsumeTimes);
  }

  @PostConstruct
  public void start() {
    client.start();
  }

  @PreDestroy
  public void close() {
    client.close();
  }

  private void consume(RocketMqEventMessage message) {
    String eventKey = textOrFallback(message.eventKey(), message.keys());
    String eventType = textOrFallback(message.eventType(), message.tag());
    notifyService.pushFromDomainEvent(eventKey, consumerGroup, eventType, message.payloadJson());
  }

  private String textOrFallback(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }
}
