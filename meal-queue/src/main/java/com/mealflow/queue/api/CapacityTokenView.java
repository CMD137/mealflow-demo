package com.mealflow.queue.api;

public record CapacityTokenView(
    long capacityTokenId,
    long merchantId,
    Long ticketId,
    Long orderId,
    String status,
    String releaseReason
) {
}
