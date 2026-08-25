package com.mealflow.promotion.mq;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mealflow.infra.event.RocketMqConsumerClient;
import com.mealflow.infra.event.RocketMqConsumerClient.RocketMqEventMessage;
import com.mealflow.promotion.seckill.ClaimSettlementResult;
import com.mealflow.promotion.seckill.SeckillClaimCommand;
import com.mealflow.promotion.seckill.VoucherClaimPendingRecoveryScheduler;
import com.mealflow.promotion.seckill.VoucherClaimSettlementService;
import com.mealflow.promotion.seckill.VoucherSeckillGuard;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "mealflow.mq.seckill-consumer", name = "enabled", havingValue = "true",
    matchIfMissing = true)
public class SeckillClaimRocketMqConsumer {
  private static final String EVENT_TYPE = "SeckillClaimRequested";

  private final ObjectMapper objectMapper;
  private final VoucherClaimSettlementService settlementService;
  private final VoucherSeckillGuard seckillGuard;
  private final VoucherClaimPendingRecoveryScheduler recoveryScheduler;
  private final RocketMqConsumerClient client;

  public SeckillClaimRocketMqConsumer(ObjectMapper objectMapper, VoucherClaimSettlementService settlementService,
      VoucherSeckillGuard seckillGuard, VoucherClaimPendingRecoveryScheduler recoveryScheduler,
      @Value("${rocketmq.name-server}") String nameServerAddress,
      @Value("${mealflow.mq.seckill-consumer.group:mealflow-promotion-seckill-consumer}") String consumerGroup,
      @Value("${mealflow.mq.seckill-consumer.topics:mealflow-seckill-commands}") String topics,
      @Value("${mealflow.mq.seckill-consumer.max-reconsume-times:5}") int maxReconsumeTimes) {
    this.objectMapper = objectMapper;
    this.settlementService = settlementService;
    this.seckillGuard = seckillGuard;
    this.recoveryScheduler = recoveryScheduler;
    this.client = new RocketMqConsumerClient(consumerGroup, nameServerAddress, Arrays.asList(topics.split(",")),
        this::consumeMessage, maxReconsumeTimes);
  }

  @PostConstruct
  public void start() {
    client.start();
  }

  @PreDestroy
  public void close() {
    client.close();
  }

  public ClaimSettlementResult consume(SeckillClaimCommand command) {
    ClaimSettlementResult result = settlementService.settle(command);
    if ("SOLD_OUT".equals(result.status())) {
      seckillGuard.compensate(command.userId(), command.voucherId());
    } else if ("CLAIMED".equals(result.status())) {
      seckillGuard.complete(command.userId(), command.voucherId());
    } else {
      throw new IllegalStateException("unsupported claim settlement status: " + result.status());
    }
    recoveryScheduler.markRecovered(command.eventKey());
    return result;
  }

  private void consumeMessage(RocketMqEventMessage message) {
    if (!EVENT_TYPE.equals(message.eventType())) {
      return;
    }
    try {
      SeckillClaimCommand command = objectMapper.readValue(message.payloadJson(), SeckillClaimCommand.class);
      String messageKey = message.eventKey() == null || message.eventKey().isBlank()
          ? message.keys() : message.eventKey();
      if (!command.eventKey().equals(messageKey)) {
        throw new IllegalArgumentException("seckill message key does not match payload");
      }
      consume(command);
    } catch (JsonProcessingException ex) {
      throw new IllegalArgumentException("invalid seckill command payload", ex);
    }
  }
}
