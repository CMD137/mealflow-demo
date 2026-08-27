package com.mealflow.common.internal;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory nonce cache that rejects replayed signed requests within the timestamp window.
 *
 * <p>Single-instance protection by design: each replica keeps its own cache. Cross-instance replay
 * protection would move this to Redis, which is a documented follow-up; the timestamp window is
 * deliberately short (default 5 minutes) so the exposure is bounded.</p>
 */
public final class NonceCache {

  private final Map<String, Long> nonces = new ConcurrentHashMap<>();
  private final int capacity;
  private final long windowMillis;

  public NonceCache(int capacity, long windowMillis) {
    this.capacity = capacity;
    this.windowMillis = windowMillis;
  }

  /** @return true if the nonce was accepted (first time seen), false on replay. */
  public boolean accept(String service, String nonce, long timestampMillis) {
    if (nonces.size() >= capacity) {
      prune(timestampMillis);
    }
    return nonces.putIfAbsent(key(service, nonce), timestampMillis) == null;
  }

  private void prune(long now) {
    long cutoff = now - windowMillis;
    nonces.entrySet().removeIf(entry -> entry.getValue() < cutoff);
  }

  private String key(String service, String nonce) {
    return service + ":" + nonce;
  }
}
