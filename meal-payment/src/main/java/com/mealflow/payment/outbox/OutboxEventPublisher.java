package com.mealflow.payment.outbox;

import com.mealflow.payment.api.LocalEventView;

public interface OutboxEventPublisher {
  void publish(LocalEventView event);
}
