package com.mealflow.queue.api;

public record QueueReadyTicket(long ticketId, String ticketNo, long capacityTokenId, QueueTicketSnapshot snapshot) {
}
