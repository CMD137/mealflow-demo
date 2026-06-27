package com.mealflow.app.queue;

import java.time.LocalDateTime;

public record QueueTicketView(
    long ticketId,
    String ticketNo,
    String status,
    int aheadCount,
    int estimatedWaitSeconds,
    LocalDateTime expireTime,
    boolean canCancel
) {
}
