package com.mealflow.infra.consumer;

import com.mealflow.common.status.ConsumerRecordStatus;
import com.mealflow.infra.id.IdGenerator;
import java.time.LocalDateTime;
import java.util.function.Supplier;

public class PersistentConsumerRecordTemplate {
  private final IdGenerator idGenerator = new IdGenerator();
  private final PersistentConsumerRecordRepository repository;

  public PersistentConsumerRecordTemplate(PersistentConsumerRecordRepository repository) {
    this.repository = repository;
  }

  public <T> T consumeOnce(String eventKey, String consumerGroup, Supplier<T> supplier) {
    String status = repository.findStatus(eventKey, consumerGroup);
    if (ConsumerRecordStatus.SUCCESS.name().equals(status)
        || ConsumerRecordStatus.PROCESSING.name().equals(status)) {
      return null;
    }
    LocalDateTime now = LocalDateTime.now();
    if (status == null) {
      repository.insertProcessing(idGenerator.next("consumerRecord"), eventKey, consumerGroup, now);
    } else {
      repository.markProcessing(eventKey, consumerGroup, now);
    }

    try {
      T result = supplier.get();
      repository.markSuccess(eventKey, consumerGroup, LocalDateTime.now());
      return result;
    } catch (RuntimeException ex) {
      repository.markFailed(eventKey, consumerGroup, trimError(ex), LocalDateTime.now());
      throw ex;
    }
  }

  private String trimError(RuntimeException ex) {
    String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
    return message.length() <= 512 ? message : message.substring(0, 512);
  }
}
