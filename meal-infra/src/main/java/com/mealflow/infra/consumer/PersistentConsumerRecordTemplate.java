package com.mealflow.infra.consumer;

import com.mealflow.common.status.ConsumerRecordStatus;
import com.mealflow.infra.id.IdGenerator;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.function.Supplier;

public class PersistentConsumerRecordTemplate {
  private static final Duration DEFAULT_PROCESSING_TIMEOUT = Duration.ofMinutes(5);

  private final IdGenerator idGenerator = new IdGenerator();
  private final PersistentConsumerRecordRepository repository;
  private final Duration processingTimeout;

  public PersistentConsumerRecordTemplate(PersistentConsumerRecordRepository repository) {
    this(repository, DEFAULT_PROCESSING_TIMEOUT);
  }

  public PersistentConsumerRecordTemplate(PersistentConsumerRecordRepository repository, Duration processingTimeout) {
    this.repository = repository;
    this.processingTimeout = processingTimeout;
  }

  public void ensureIdAtLeast(long value) {
    idGenerator.ensureAtLeast("consumerRecord", value);
  }

  public int recoverProcessingTimeouts() {
    LocalDateTime now = LocalDateTime.now();
    return repository.markProcessingTimeoutsBefore(now.minus(processingTimeout), now);
  }

  public <T> T consumeOnce(String eventKey, String consumerGroup, Supplier<T> supplier) {
    PersistentConsumerRecordState record = repository.findRecord(eventKey, consumerGroup);
    LocalDateTime now = LocalDateTime.now();
    if (record != null && ConsumerRecordStatus.SUCCESS.name().equals(record.getStatus())) {
      return null;
    }
    if (record != null && ConsumerRecordStatus.PROCESSING.name().equals(record.getStatus())) {
      LocalDateTime timeoutBefore = now.minus(processingTimeout);
      if (record.getUpdateTime() == null || !record.getUpdateTime().isBefore(timeoutBefore)) {
        return null;
      }
      int timedOut = repository.markTimeoutBefore(eventKey, consumerGroup, timeoutBefore, now);
      if (timedOut == 0) {
        return null;
      }
      record.setStatus(ConsumerRecordStatus.TIMEOUT.name());
    }
    if (record == null) {
      repository.insertProcessing(idGenerator.next("consumerRecord"), eventKey, consumerGroup, now);
    } else if (repository.markProcessing(eventKey, consumerGroup, now) == 0) {
      return null;
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
