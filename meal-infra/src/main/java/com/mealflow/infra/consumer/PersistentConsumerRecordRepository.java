package com.mealflow.infra.consumer;

import java.time.LocalDateTime;

public interface PersistentConsumerRecordRepository {
  PersistentConsumerRecordState findRecord(String eventKey, String consumerGroup);

  String findStatus(String eventKey, String consumerGroup);

  int insertProcessing(long id, String eventKey, String consumerGroup, String eventType, String payloadJson,
      LocalDateTime now);

  int markProcessing(String eventKey, String consumerGroup, String eventType, String payloadJson, LocalDateTime now);

  int markTimeoutBefore(String eventKey, String consumerGroup, LocalDateTime before, LocalDateTime now);

  int markProcessingTimeoutsBefore(LocalDateTime before, LocalDateTime now);

  int markSuccess(String eventKey, String consumerGroup, LocalDateTime now);

  int markFailed(String eventKey, String consumerGroup, String lastError, LocalDateTime now);
}
