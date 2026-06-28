package com.mealflow.queue.waiting;

import java.util.Collection;
import java.util.Optional;

public interface WaitingQueueStore {
  void rebuild(Collection<WaitingTicketEntry> waitingTickets);

  void add(long merchantId, WaitingTicketEntry ticket);

  void remove(long merchantId, WaitingTicketEntry ticket);

  Optional<WaitingTicketEntry> poll(long merchantId);

  int size(long merchantId);

  int rank(long merchantId, WaitingTicketEntry ticket, int fallback);
}
