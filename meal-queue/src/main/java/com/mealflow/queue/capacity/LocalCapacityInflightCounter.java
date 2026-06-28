package com.mealflow.queue.capacity;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "mealflow.queue", name = "inflight-counter", havingValue = "local",
    matchIfMissing = true)
public class LocalCapacityInflightCounter implements CapacityInflightCounter {
  private final Map<Long, AtomicInteger> heldCounts = new ConcurrentHashMap<>();

  @Override
  public synchronized void rebuild(Map<Long, Integer> heldCounts) {
    this.heldCounts.clear();
    heldCounts.forEach((merchantId, count) -> this.heldCounts.put(merchantId, new AtomicInteger(Math.max(0, count))));
  }

  @Override
  public void increment(long merchantId) {
    counter(merchantId).incrementAndGet();
  }

  @Override
  public void decrement(long merchantId) {
    counter(merchantId).updateAndGet(value -> Math.max(0, value - 1));
  }

  @Override
  public int count(long merchantId) {
    return counter(merchantId).get();
  }

  private AtomicInteger counter(long merchantId) {
    return heldCounts.computeIfAbsent(merchantId, ignored -> new AtomicInteger());
  }
}
