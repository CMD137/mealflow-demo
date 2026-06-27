package com.mealflow.queue.api;

public record ReleaseCapacityResponse(boolean released, QueueReadyTicket readyTicket) {
}
