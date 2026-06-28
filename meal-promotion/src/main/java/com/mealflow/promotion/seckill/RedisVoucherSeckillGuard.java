package com.mealflow.promotion.seckill;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "mealflow.promotion", name = "seckill-mode", havingValue = "redis")
public class RedisVoucherSeckillGuard implements VoucherSeckillGuard {
  private static final Long ACCEPTED = 0L;
  private static final Long SOLD_OUT = 1L;
  private static final Long DUPLICATE = 2L;

  private static final DefaultRedisScript<Long> CLAIM_SCRIPT = new DefaultRedisScript<>("""
      if redis.call('exists', KEYS[1]) == 0 then
        redis.call('set', KEYS[1], ARGV[2])
      end
      if redis.call('sismember', KEYS[2], ARGV[1]) == 1 then
        return 2
      end
      local stock = tonumber(redis.call('get', KEYS[1]))
      if stock == nil or stock <= 0 then
        return 1
      end
      redis.call('decr', KEYS[1])
      redis.call('sadd', KEYS[2], ARGV[1])
      return 0
      """, Long.class);

  private final StringRedisTemplate redisTemplate;

  public RedisVoucherSeckillGuard(StringRedisTemplate redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  @Override
  public ClaimResult tryClaim(long userId, long voucherId, int initialStock) {
    Long result = redisTemplate.execute(CLAIM_SCRIPT,
        List.of(stockKey(voucherId), userSetKey(voucherId)),
        String.valueOf(userId),
        String.valueOf(Math.max(initialStock, 0)));
    if (ACCEPTED.equals(result)) {
      return ClaimResult.ACCEPTED;
    }
    if (DUPLICATE.equals(result)) {
      return ClaimResult.DUPLICATE;
    }
    if (SOLD_OUT.equals(result)) {
      return ClaimResult.SOLD_OUT;
    }
    throw new IllegalStateException("unknown Redis voucher claim result: " + result);
  }

  @Override
  public void compensate(long userId, long voucherId) {
    redisTemplate.opsForValue().increment(stockKey(voucherId));
    redisTemplate.opsForSet().remove(userSetKey(voucherId), String.valueOf(userId));
  }

  @Override
  public Set<Long> claimedUsers(long voucherId) {
    Set<String> members = redisTemplate.opsForSet().members(userSetKey(voucherId));
    if (members == null || members.isEmpty()) {
      return Set.of();
    }
    return members.stream().map(Long::parseLong).collect(Collectors.toSet());
  }

  private String stockKey(long voucherId) {
    return "voucher:stock:" + voucherId;
  }

  private String userSetKey(long voucherId) {
    return "voucher:user:" + voucherId;
  }
}
