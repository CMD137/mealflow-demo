package com.mealflow.notify.api;

import java.time.LocalDateTime;

public record ConsumerRecordView(
    long id,
    String eventKey,
    String consumerGroup,
    String eventType,
    String status,
    String lastError,
    LocalDateTime createTime,
    LocalDateTime updateTime
) {
}
