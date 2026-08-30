package com.mealflow.queue.api;

/** Internal recovery payload for a promoted ticket that has no order yet. */
public record RecoverableQueueTicket(long ticketId, long capacityTokenId) {
}
