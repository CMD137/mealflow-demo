package com.mealflow.infra.event;

import java.nio.charset.StandardCharsets;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;

public class RocketMqOutboxClient implements AutoCloseable {
  private final String producerGroup;
  private final String nameServerAddress;
  private final String topic;
  private DefaultMQProducer producer;

  public RocketMqOutboxClient(String producerGroup, String nameServerAddress, String topic) {
    this.producerGroup = requireText(producerGroup, "producerGroup");
    this.nameServerAddress = requireText(nameServerAddress, "nameServerAddress");
    this.topic = requireText(topic, "topic");
  }

  public SendResult publish(String eventKey, String eventType, String payloadJson) {
    try {
      DefaultMQProducer currentProducer = producer();
      Message message = new Message(topic, eventType, eventKey, payloadJson.getBytes(StandardCharsets.UTF_8));
      message.putUserProperty("eventKey", eventKey);
      message.putUserProperty("eventType", eventType);
      return currentProducer.send(message);
    } catch (Exception ex) {
      throw new IllegalStateException("failed to publish outbox event to RocketMQ", ex);
    }
  }

  private synchronized DefaultMQProducer producer() throws Exception {
    if (producer == null) {
      producer = new DefaultMQProducer(producerGroup);
      producer.setNamesrvAddr(nameServerAddress);
      producer.start();
    }
    return producer;
  }

  @Override
  public synchronized void close() {
    if (producer != null) {
      producer.shutdown();
      producer = null;
    }
  }

  private static String requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    return value;
  }
}
