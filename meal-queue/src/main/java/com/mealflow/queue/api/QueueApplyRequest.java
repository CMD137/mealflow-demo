package com.mealflow.queue.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public record QueueApplyRequest(
    @NotBlank String requestId,
    long userId,
    long merchantId,
    @NotNull QueueTicketSnapshot snapshot,
    @NotNull LocalDateTime expireTime,
    long priorityWeightMillis
) {
}
