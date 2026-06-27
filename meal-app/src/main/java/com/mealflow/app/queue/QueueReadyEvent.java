package com.mealflow.app.queue;

public record QueueReadyEvent(long ticketId, String ticketNo, long capacityTokenId) {
}
