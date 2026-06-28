package com.mealflow.order.api;

import java.time.LocalDateTime;

public record LocalEventView(
    long id,
    String eventKey,
    String eventType,
    int eventVersion,
    String aggregateType,
    long aggregateId,
    String payloadJson,
    String status,
    int retryCount,
    String lastError,
    LocalDateTime createTime,
    LocalDateTime updateTime
) {
}
