package com.mealflow.fulfillment.metrics;

import com.mealflow.fulfillment.mapper.LocalEventMapper;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class FulfillmentBusinessMetrics {
  private static final List<String> OUTBOX_STATUSES = List.of("NEW", "SENDING", "SENT", "FAILED");

  public FulfillmentBusinessMetrics(MeterRegistry registry, LocalEventMapper localEventMapper) {
    OUTBOX_STATUSES.forEach(status -> Gauge.builder("mealflow.outbox.events", localEventMapper,
            mapper -> mapper.countByStatus(status))
        .description("Fulfillment outbox event count by status")
        .tag("service", "fulfillment")
        .tag("status", status)
        .register(registry));
  }
}
