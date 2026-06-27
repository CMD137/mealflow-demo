package com.mealflow.infra.id;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class IdGenerator {
  private final ConcurrentHashMap<String, AtomicLong> sequences = new ConcurrentHashMap<>();

  public long next(String namespace) {
    return sequences.computeIfAbsent(namespace, ignored -> new AtomicLong(10_000)).incrementAndGet();
  }
}
