package com.mealflow.order.metrics;

import com.mealflow.order.mapper.ConsumerRecordMapper;
import com.mealflow.order.mapper.LocalEventMapper;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class OrderBusinessMetrics {
  private static final List<String> OUTBOX_STATUSES = List.of("NEW", "SENDING", "SENT", "FAILED");
  private static final List<String> CONSUMER_STATUSES = List.of("PROCESSING", "SUCCESS", "FAILED", "TIMEOUT");

  public OrderBusinessMetrics(MeterRegistry registry, LocalEventMapper localEventMapper,
      ConsumerRecordMapper consumerRecordMapper) {
    OUTBOX_STATUSES.forEach(status -> Gauge.builder("mealflow.outbox.events", localEventMapper,
            mapper -> mapper.countByStatus(status))
        .description("Order outbox event count by status")
        .tag("service", "order")
        .tag("status", status)
        .register(registry));
    CONSUMER_STATUSES.forEach(status -> Gauge.builder("mealflow.consumer.records", consumerRecordMapper,
            mapper -> mapper.countByStatus(status))
        .description("Order consumer record count by status")
        .tag("service", "order")
        .tag("status", status)
        .register(registry));
  }
}
