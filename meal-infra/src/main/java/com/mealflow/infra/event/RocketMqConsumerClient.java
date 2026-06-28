package com.mealflow.infra.event;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.function.Consumer;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.message.MessageExt;

public class RocketMqConsumerClient implements AutoCloseable {
  private final String consumerGroup;
  private final String nameServerAddress;
  private final Collection<String> topics;
  private final Consumer<RocketMqEventMessage> messageConsumer;
  private DefaultMQPushConsumer consumer;

  public RocketMqConsumerClient(String consumerGroup, String nameServerAddress, Collection<String> topics,
      Consumer<RocketMqEventMessage> messageConsumer) {
    this.consumerGroup = requireText(consumerGroup, "consumerGroup");
    this.nameServerAddress = requireText(nameServerAddress, "nameServerAddress");
    this.topics = topics.stream().map(String::trim).filter(topic -> !topic.isBlank()).toList();
    if (this.topics.isEmpty()) {
      throw new IllegalArgumentException("topics must not be empty");
    }
    this.messageConsumer = messageConsumer;
  }

  public synchronized void start() {
    if (consumer != null) {
      return;
    }
    try {
      DefaultMQPushConsumer currentConsumer = new DefaultMQPushConsumer(consumerGroup);
      currentConsumer.setNamesrvAddr(nameServerAddress);
      for (String topic : topics) {
        currentConsumer.subscribe(topic, "*");
      }
      currentConsumer.registerMessageListener((MessageListenerConcurrently) (messages, context) -> {
        try {
          for (MessageExt message : messages) {
            messageConsumer.accept(toEventMessage(message));
          }
          return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        } catch (RuntimeException ex) {
          return ConsumeConcurrentlyStatus.RECONSUME_LATER;
        }
      });
      currentConsumer.start();
      consumer = currentConsumer;
    } catch (Exception ex) {
      throw new IllegalStateException("failed to start RocketMQ consumer", ex);
    }
  }

  @Override
  public synchronized void close() {
    if (consumer != null) {
      consumer.shutdown();
      consumer = null;
    }
  }

  private RocketMqEventMessage toEventMessage(MessageExt message) {
    return new RocketMqEventMessage(
        message.getTopic(),
        message.getTags(),
        message.getKeys(),
        message.getUserProperty("eventKey"),
        message.getUserProperty("eventType"),
        new String(message.getBody(), StandardCharsets.UTF_8));
  }

  private static String requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    return value;
  }

  public record RocketMqEventMessage(
      String topic,
      String tag,
      String keys,
      String eventKey,
      String eventType,
      String payloadJson
  ) {
  }
}
