package com.mealflow.promotion.seckill;

import java.util.List;
import java.util.Set;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
public class RedisVoucherSeckillGuard implements VoucherSeckillGuard {
  private static final Long ACCEPTED = 0L;
  private static final Long SOLD_OUT = 1L;
  private static final Long DUPLICATE = 2L;
  private static final Long STOCK_MISSING = 3L;
  private static final String STATE_INITIALIZED_KEY = "seckill:state:initialized";

  private static final DefaultRedisScript<Long> CLAIM_SCRIPT = new DefaultRedisScript<>("""
      if redis.call('exists', KEYS[1]) == 0 then
        return 3
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
      redis.call('zadd', KEYS[3], ARGV[2], ARGV[1])
      return 0
      """, Long.class);

  private static final DefaultRedisScript<Long> COMPENSATE_SCRIPT = new DefaultRedisScript<>("""
      if redis.call('srem', KEYS[2], ARGV[1]) == 1 and redis.call('exists', KEYS[1]) == 1 then
        redis.call('incr', KEYS[1])
      end
      redis.call('zrem', KEYS[3], ARGV[1])
      return 1
      """, Long.class);

  private final StringRedisTemplate redisTemplate;

  public RedisVoucherSeckillGuard(StringRedisTemplate redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  @Override
  public ClaimResult tryClaim(long userId, long voucherId, long nextRetryTime) {
    Long result = redisTemplate.execute(CLAIM_SCRIPT,
        List.of(stockKey(voucherId), userSetKey(voucherId), pendingKey(voucherId)),
        String.valueOf(userId),
        String.valueOf(nextRetryTime));
    if (ACCEPTED.equals(result)) {
      return ClaimResult.ACCEPTED;
    }
    if (DUPLICATE.equals(result)) {
      return ClaimResult.DUPLICATE;
    }
    if (SOLD_OUT.equals(result)) {
      return ClaimResult.SOLD_OUT;
    }
    if (STOCK_MISSING.equals(result)) {
      return ClaimResult.STOCK_MISSING;
    }
    throw new IllegalStateException("unknown Redis voucher claim result: " + result);
  }

  @Override
  public void compensate(long userId, long voucherId) {
    redisTemplate.execute(COMPENSATE_SCRIPT,
        List.of(stockKey(voucherId), userSetKey(voucherId), pendingKey(voucherId)),
        String.valueOf(userId));
  }

  @Override
  public void complete(long userId, long voucherId) {
    redisTemplate.opsForZSet().remove(pendingKey(voucherId), String.valueOf(userId));
  }

  @Override
  public void syncStock(long voucherId, int stock) {
    redisTemplate.opsForValue().set(stockKey(voucherId), String.valueOf(Math.max(stock, 0)));
  }

  @Override
  public boolean syncStockIfAbsent(long voucherId, int stock) {
    return Boolean.TRUE.equals(redisTemplate.opsForValue()
        .setIfAbsent(stockKey(voucherId), String.valueOf(Math.max(stock, 0))));
  }

  @Override
  public Set<Long> findDuePending(long voucherId, long now, int limit) {
    if (limit <= 0) {
      return Set.of();
    }
    Set<String> members = redisTemplate.opsForZSet()
        .rangeByScore(pendingKey(voucherId), 0, now, 0, limit);
    if (members == null || members.isEmpty()) {
      return Set.of();
    }
    return members.stream().map(Long::parseLong)
        .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
  }

  @Override
  public void delayPending(long userId, long voucherId, long nextRetryTime) {
    redisTemplate.opsForZSet().add(pendingKey(voucherId), String.valueOf(userId), nextRetryTime);
  }

  @Override
  public boolean isClaimed(long userId, long voucherId) {
    return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(userSetKey(voucherId), String.valueOf(userId)));
  }

  @Override
  public boolean isPending(long userId, long voucherId) {
    return redisTemplate.opsForZSet().score(pendingKey(voucherId), String.valueOf(userId)) != null;
  }

  @Override
  public void recordClaimed(long userId, long voucherId) {
    redisTemplate.opsForSet().add(userSetKey(voucherId), String.valueOf(userId));
  }

  @Override
  public long pendingCount(long voucherId) {
    Long count = redisTemplate.opsForZSet().zCard(pendingKey(voucherId));
    return count == null ? 0 : count;
  }

  @Override
  public boolean isStateInitialized() {
    return Boolean.TRUE.equals(redisTemplate.hasKey(STATE_INITIALIZED_KEY));
  }

  @Override
  public void markStateInitialized() {
    redisTemplate.opsForValue().set(STATE_INITIALIZED_KEY, "1");
  }

  private String stockKey(long voucherId) {
    return "seckill:{" + voucherId + "}:stock";
  }

  private String userSetKey(long voucherId) {
    return "seckill:{" + voucherId + "}:users";
  }

  private String pendingKey(long voucherId) {
    return "seckill:{" + voucherId + "}:pending";
  }
}
