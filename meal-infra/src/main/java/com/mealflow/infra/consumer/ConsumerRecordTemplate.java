package com.mealflow.infra.consumer;

import com.mealflow.common.status.ConsumerRecordStatus;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class ConsumerRecordTemplate {
  private final ConcurrentHashMap<String, ConsumerRecordStatus> records = new ConcurrentHashMap<>();

  public <T> T consumeOnce(String eventKey, String consumerGroup, Supplier<T> supplier) {
    String key = eventKey + ":" + consumerGroup;
    ConsumerRecordStatus status = records.get(key);
    if (status == ConsumerRecordStatus.SUCCESS) {
      return null;
    }
    records.put(key, ConsumerRecordStatus.PROCESSING);
    try {
      T result = supplier.get();
      records.put(key, ConsumerRecordStatus.SUCCESS);
      return result;
    } catch (RuntimeException ex) {
      records.put(key, ConsumerRecordStatus.FAILED);
      throw ex;
    }
  }
}
