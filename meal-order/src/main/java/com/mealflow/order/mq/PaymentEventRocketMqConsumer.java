package com.mealflow.order.mq;

import com.mealflow.infra.event.RocketMqConsumerClient;
import com.mealflow.infra.event.RocketMqConsumerClient.RocketMqEventMessage;
import com.mealflow.order.OrderService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "mealflow.mq.payment-consumer", name = "enabled", havingValue = "true")
public class PaymentEventRocketMqConsumer {
  private static final String EVENT_PAYMENT_PAID = "PaymentPaid";

  private final OrderService orderService;
  private final RocketMqConsumerClient client;
  private final String consumerGroup;

  public PaymentEventRocketMqConsumer(
      OrderService orderService,
      @Value("${rocketmq.name-server}") String nameServerAddress,
      @Value("${mealflow.mq.payment-consumer.group:mealflow-order-payment-consumer}") String consumerGroup,
      @Value("${mealflow.mq.payment-consumer.topics:mealflow-payment-events}") String topics,
      @Value("${mealflow.mq.payment-consumer.max-reconsume-times:16}") int maxReconsumeTimes) {
    this.orderService = orderService;
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
    if (!EVENT_PAYMENT_PAID.equals(message.eventType())) {
      return;
    }
    String eventKey = message.eventKey() == null || message.eventKey().isBlank() ? message.keys() : message.eventKey();
    orderService.consumePaymentPaid(eventKey, consumerGroup, message.payloadJson());
  }
}
