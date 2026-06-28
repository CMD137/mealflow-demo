package com.mealflow.infra.consumer;

import java.time.LocalDateTime;

public interface PersistentConsumerRecordRepository {
  String findStatus(String eventKey, String consumerGroup);

  int insertProcessing(long id, String eventKey, String consumerGroup, LocalDateTime now);

  int markProcessing(String eventKey, String consumerGroup, LocalDateTime now);

  int markSuccess(String eventKey, String consumerGroup, LocalDateTime now);

  int markFailed(String eventKey, String consumerGroup, String lastError, LocalDateTime now);
}
