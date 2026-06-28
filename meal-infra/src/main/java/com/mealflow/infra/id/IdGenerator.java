package com.mealflow.infra.id;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class IdGenerator {
  private static final long INITIAL_VALUE = 10_000;

  private final ConcurrentHashMap<String, AtomicLong> sequences = new ConcurrentHashMap<>();

  public long next(String namespace) {
    return sequences.computeIfAbsent(namespace, ignored -> new AtomicLong(INITIAL_VALUE)).incrementAndGet();
  }

  public void ensureAtLeast(String namespace, long value) {
    sequences.compute(namespace, (ignored, sequence) -> {
      AtomicLong current = sequence == null ? new AtomicLong(INITIAL_VALUE) : sequence;
      current.updateAndGet(existing -> Math.max(existing, value));
      return current;
    });
  }
}
