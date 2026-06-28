package com.mealflow.queue.waiting;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "mealflow.queue", name = "waiting-store", havingValue = "local", matchIfMissing = true)
public class LocalWaitingQueueStore implements WaitingQueueStore {
  private final Map<Long, PriorityQueue<WaitingTicketEntry>> waitingQueues = new ConcurrentHashMap<>();

  @Override
  public synchronized void rebuild(Collection<WaitingTicketEntry> waitingTickets) {
    waitingQueues.clear();
    waitingTickets.forEach(ticket -> queue(ticket.merchantId()).add(ticket));
  }

  public synchronized void rebuildForMerchant(long merchantId, Collection<WaitingTicketEntry> waitingTickets) {
    queue(merchantId).clear();
    queue(merchantId).addAll(waitingTickets);
  }

  @Override
  public synchronized void add(long merchantId, WaitingTicketEntry ticket) {
    queue(merchantId).add(ticket);
  }

  @Override
  public synchronized void remove(long merchantId, WaitingTicketEntry ticket) {
    queue(merchantId).removeIf(waiting -> waiting.ticketId() == ticket.ticketId());
  }

  @Override
  public synchronized Optional<WaitingTicketEntry> poll(long merchantId) {
    return Optional.ofNullable(queue(merchantId).poll());
  }

  @Override
  public synchronized int size(long merchantId) {
    return queue(merchantId).size();
  }

  @Override
  public synchronized int rank(long merchantId, WaitingTicketEntry ticket, int fallback) {
    List<WaitingTicketEntry> waiting = new ArrayList<>(queue(merchantId));
    waiting.sort(Comparator.comparingLong(WaitingTicketEntry::score).thenComparing(WaitingTicketEntry::ticketNo));
    for (int i = 0; i < waiting.size(); i++) {
      if (waiting.get(i).ticketId() == ticket.ticketId()) {
        return i;
      }
    }
    return fallback;
  }

  private PriorityQueue<WaitingTicketEntry> queue(long merchantId) {
    return waitingQueues.computeIfAbsent(merchantId, ignored -> new PriorityQueue<>(
        Comparator.comparingLong(WaitingTicketEntry::score).thenComparing(WaitingTicketEntry::ticketNo)));
  }
}
