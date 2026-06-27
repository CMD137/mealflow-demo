package com.mealflow.app.queue;

import java.time.LocalDateTime;

public record QueueApplyCommand(
    String requestId,
    long userId,
    long merchantId,
    QueueTicketSnapshot snapshot,
    LocalDateTime expireTime,
    long priorityWeightMillis
) {
}
