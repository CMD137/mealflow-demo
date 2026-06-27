package com.mealflow.queue.api;

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
