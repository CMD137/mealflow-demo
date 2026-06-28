package com.mealflow.queue.capacity;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "mealflow.queue", name = "inflight-counter", havingValue = "redis")
public class RedisCapacityInflightCounter implements CapacityInflightCounter {
  private static final String KEY_PREFIX = "capacity:merchant:";
  private static final String KEY_SUFFIX = ":inflight";
  private static final DefaultRedisScript<Long> SAFE_DECREMENT_SCRIPT = new DefaultRedisScript<>("""
      local current = tonumber(redis.call('GET', KEYS[1]) or '0')
      if current <= 0 then
        redis.call('SET', KEYS[1], 0)
        return 0
      end
      return redis.call('DECR', KEYS[1])
      """, Long.class);

  private final StringRedisTemplate redisTemplate;
  private final LocalCapacityInflightCounter fallback = new LocalCapacityInflightCounter();

  public RedisCapacityInflightCounter(StringRedisTemplate redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  @Override
  public void rebuild(Map<Long, Integer> heldCounts) {
    fallback.rebuild(heldCounts);
    try {
      Set<String> staleKeys = redisTemplate.keys(KEY_PREFIX + "*" + KEY_SUFFIX);
      if (staleKeys != null && !staleKeys.isEmpty()) {
        redisTemplate.delete(staleKeys);
      }
      heldCounts.forEach((merchantId, count) -> redisTemplate.opsForValue().set(key(merchantId),
          String.valueOf(Math.max(0, count))));
    } catch (RuntimeException ignored) {
      // Fallback has already been rebuilt from MySQL facts.
    }
  }

  @Override
  public void increment(long merchantId) {
    fallback.increment(merchantId);
    try {
      redisTemplate.opsForValue().increment(key(merchantId));
    } catch (RuntimeException ignored) {
      // Local fallback already reflects the derived count.
    }
  }

  @Override
  public void decrement(long merchantId) {
    fallback.decrement(merchantId);
    try {
      redisTemplate.execute(SAFE_DECREMENT_SCRIPT, List.of(key(merchantId)));
    } catch (RuntimeException ignored) {
      // Local fallback already reflects the derived count.
    }
  }

  @Override
  public int count(long merchantId) {
    try {
      String value = redisTemplate.opsForValue().get(key(merchantId));
      return value == null ? fallback.count(merchantId) : Math.max(0, Integer.parseInt(value));
    } catch (RuntimeException ex) {
      return fallback.count(merchantId);
    }
  }

  private String key(long merchantId) {
    return KEY_PREFIX + merchantId + KEY_SUFFIX;
  }
}
