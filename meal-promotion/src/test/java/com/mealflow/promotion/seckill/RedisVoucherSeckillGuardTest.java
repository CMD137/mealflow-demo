package com.mealflow.promotion.seckill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

@ExtendWith(MockitoExtension.class)
class RedisVoucherSeckillGuardTest {
  @Mock
  private StringRedisTemplate redisTemplate;

  @Mock
  private ValueOperations<String, String> valueOperations;

  @Mock
  private ZSetOperations<String, String> zSetOperations;

  @Mock
  private SetOperations<String, String> setOperations;

  @Test
  void translatesMissingStockLuaResultToDedicatedInternalState() {
    when(redisTemplate.execute(any(DefaultRedisScript.class), any(List.class), any(), any()))
        .thenReturn(3L);

    VoucherSeckillGuard.ClaimResult result = new RedisVoucherSeckillGuard(redisTemplate)
        .tryClaim(101L, 1000L, 1_000L);

    assertThat(result).isEqualTo(VoucherSeckillGuard.ClaimResult.STOCK_MISSING);
  }

  @Test
  void soldOutCompensationOnlyIncrementsWhenUserMarkerWasRemovedAndStockExists() {
    RedisVoucherSeckillGuard guard = new RedisVoucherSeckillGuard(redisTemplate);

    guard.compensate(101L, 1000L);

    ArgumentCaptor<DefaultRedisScript<Long>> scriptCaptor = ArgumentCaptor.forClass(DefaultRedisScript.class);
    verify(redisTemplate).execute(scriptCaptor.capture(), eq(List.of(
        "seckill:{1000}:stock", "seckill:{1000}:users", "seckill:{1000}:pending")), eq("101"));
    assertThat(scriptCaptor.getValue().getScriptAsString())
        .contains("srem', KEYS[2], ARGV[1]) == 1 and redis.call('exists', KEYS[1]) == 1")
        .contains("zrem', KEYS[3], ARGV[1]");
  }

  @Test
  void usesVoucherScopedPendingZSetAndNonExpiringSetNxStateOperations() {
    when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
    when(zSetOperations.zCard("seckill:{1000}:pending")).thenReturn(2L);
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.setIfAbsent("seckill:{1000}:stock", "73")).thenReturn(true);

    RedisVoucherSeckillGuard guard = new RedisVoucherSeckillGuard(redisTemplate);

    assertThat(guard.pendingCount(1000L)).isEqualTo(2L);
    assertThat(guard.syncStockIfAbsent(1000L, 73)).isTrue();
    guard.markStateInitialized();

    verify(zSetOperations).zCard("seckill:{1000}:pending");
    verify(valueOperations).setIfAbsent("seckill:{1000}:stock", "73");
    verify(valueOperations).set("seckill:state:initialized", "1");
  }

  @Test
  void recordsDurableClaimInVoucherScopedUserSet() {
    when(redisTemplate.opsForSet()).thenReturn(setOperations);

    new RedisVoucherSeckillGuard(redisTemplate).recordClaimed(101L, 1000L);

    verify(setOperations).add("seckill:{1000}:users", "101");
  }
}
