package com.mealflow.order.outbox;

import com.mealflow.order.api.LocalEventView;

public interface OutboxEventPublisher {
  void publish(LocalEventView event);
}
