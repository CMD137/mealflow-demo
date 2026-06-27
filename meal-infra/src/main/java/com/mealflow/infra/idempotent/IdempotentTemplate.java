package com.mealflow.infra.idempotent;

import com.mealflow.common.api.ErrorCode;
import com.mealflow.common.exception.BizException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class IdempotentTemplate {
  private final ConcurrentHashMap<String, Entry<?>> entries = new ConcurrentHashMap<>();

  public <T> T execute(String key, Supplier<T> supplier) {
    Entry<?> existing = entries.get(key);
    if (existing != null) {
      if (existing.processing) {
        throw new BizException(ErrorCode.IDEMPOTENT_PROCESSING);
      }
      @SuppressWarnings("unchecked")
      T snapshot = (T) existing.result;
      return snapshot;
    }

    Entry<T> processing = new Entry<>(true, null);
    Entry<?> raced = entries.putIfAbsent(key, processing);
    if (raced != null) {
      return execute(key, supplier);
    }

    try {
      T result = supplier.get();
      entries.put(key, new Entry<>(false, result));
      return result;
    } catch (RuntimeException ex) {
      entries.remove(key);
      throw ex;
    }
  }

  private record Entry<T>(boolean processing, T result) {
  }
}
