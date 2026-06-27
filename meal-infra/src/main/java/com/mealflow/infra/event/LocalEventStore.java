package com.mealflow.infra.event;

import com.mealflow.common.status.LocalEventStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class LocalEventStore {
  private final ConcurrentHashMap<String, StoredEvent> events = new ConcurrentHashMap<>();

  public void append(EventEnvelope envelope) {
    events.putIfAbsent(envelope.eventKey(), new StoredEvent(envelope, LocalEventStatus.NEW));
  }

  public void markSent(String eventKey) {
    events.computeIfPresent(eventKey, (ignored, event) -> new StoredEvent(event.envelope(), LocalEventStatus.SENT));
  }

  public List<StoredEvent> list() {
    return new ArrayList<>(events.values());
  }

  public record StoredEvent(EventEnvelope envelope, LocalEventStatus status) {
  }
}
