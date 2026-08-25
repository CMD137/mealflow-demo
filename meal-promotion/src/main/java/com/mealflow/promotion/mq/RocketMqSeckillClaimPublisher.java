package com.mealflow.promotion.mq;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mealflow.infra.event.RocketMqOutboxClient;
import com.mealflow.promotion.seckill.SeckillClaimCommand;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RocketMqSeckillClaimPublisher implements SeckillClaimPublisher {
  private static final String EVENT_TYPE = "SeckillClaimRequested";

  private final ObjectMapper objectMapper;
  private final RocketMqOutboxClient client;

  public RocketMqSeckillClaimPublisher(ObjectMapper objectMapper,
      @Value("${rocketmq.name-server}") String nameServerAddress,
      @Value("${rocketmq.producer.group:mealflow-promotion-seckill-producer}") String producerGroup,
      @Value("${mealflow.promotion.seckill.topic:mealflow-seckill-commands}") String topic) {
    this.objectMapper = objectMapper;
    this.client = new RocketMqOutboxClient(producerGroup, nameServerAddress, topic);
  }

  @Override
  public void publish(SeckillClaimCommand command) {
    try {
      client.publish(command.eventKey(), EVENT_TYPE, objectMapper.writeValueAsString(command));
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("failed to serialize seckill command", ex);
    }
  }

  @PreDestroy
  public void close() {
    client.close();
  }
}
