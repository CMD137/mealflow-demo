package com.mealflow.fulfillment.outbox;

import com.mealflow.fulfillment.api.LocalEventView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LoggingOutboxEventPublisher implements OutboxEventPublisher {
  private static final Logger log = LoggerFactory.getLogger(LoggingOutboxEventPublisher.class);

  @Override
  public void publish(LocalEventView event) {
    log.info("fulfillment outbox event ready: key={}, type={}, aggregateId={}",
        event.eventKey(), event.eventType(), event.aggregateId());
  }
}
