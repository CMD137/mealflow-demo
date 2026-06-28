package com.mealflow.fulfillment.outbox;

import com.mealflow.fulfillment.api.LocalEventView;

public interface OutboxEventPublisher {
  void publish(LocalEventView event);
}
