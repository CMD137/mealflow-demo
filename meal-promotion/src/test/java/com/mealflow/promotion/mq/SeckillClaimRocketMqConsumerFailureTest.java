package com.mealflow.promotion.mq;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mealflow.promotion.seckill.SeckillClaimCommand;
import com.mealflow.promotion.seckill.VoucherClaimPendingRecoveryScheduler;
import com.mealflow.promotion.seckill.VoucherClaimSettlementService;
import com.mealflow.promotion.seckill.VoucherSeckillGuard;
import org.junit.jupiter.api.Test;

class SeckillClaimRocketMqConsumerFailureTest {
  @Test
  void databaseFailureEscapesConsumerSoRocketMqCanRedeliver() {
    VoucherClaimSettlementService settlementService = mock(VoucherClaimSettlementService.class);
    VoucherSeckillGuard seckillGuard = mock(VoucherSeckillGuard.class);
    VoucherClaimPendingRecoveryScheduler recoveryScheduler = mock(VoucherClaimPendingRecoveryScheduler.class);
    SeckillClaimCommand command = SeckillClaimCommand.of(1000L, 42L);
    when(settlementService.settle(command)).thenThrow(new IllegalStateException("database unavailable"));
    SeckillClaimRocketMqConsumer consumer = new SeckillClaimRocketMqConsumer(new ObjectMapper(), settlementService,
        seckillGuard, recoveryScheduler, "localhost:9876", "test-consumer", "test-topic", 5);

    assertThatThrownBy(() -> consumer.consume(command))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("database unavailable");
    verify(seckillGuard, never()).complete(42L, 1000L);
    verify(seckillGuard, never()).compensate(42L, 1000L);
  }
}
