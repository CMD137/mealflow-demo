package com.mealflow.queue.capacity;

import java.util.Map;

public interface CapacityInflightCounter {
  void rebuild(Map<Long, Integer> heldCounts);

  void increment(long merchantId);

  void decrement(long merchantId);

  int count(long merchantId);
}
