package com.mealflow.order.outbox;

import com.mealflow.order.api.LocalEventView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "mealflow.outbox", name = "publisher", havingValue = "log", matchIfMissing = true)
public class LoggingOutboxEventPublisher implements OutboxEventPublisher {
  private static final Logger log = LoggerFactory.getLogger(LoggingOutboxEventPublisher.class);

  @Override
  public void publish(LocalEventView event) {
    log.info("order outbox event ready: key={}, type={}, aggregateId={}",
        event.eventKey(), event.eventType(), event.aggregateId());
  }
}
