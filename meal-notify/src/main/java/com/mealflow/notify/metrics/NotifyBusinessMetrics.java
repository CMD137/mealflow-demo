package com.mealflow.notify.metrics;

import com.mealflow.notify.mapper.ConsumerRecordMapper;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class NotifyBusinessMetrics {
  private static final List<String> CONSUMER_STATUSES = List.of("PROCESSING", "SUCCESS", "FAILED", "TIMEOUT");

  public NotifyBusinessMetrics(MeterRegistry registry, ConsumerRecordMapper consumerRecordMapper) {
    CONSUMER_STATUSES.forEach(status -> Gauge.builder("mealflow.consumer.records", consumerRecordMapper,
            mapper -> mapper.countByStatus(status))
        .description("Notify consumer record count by status")
        .tag("service", "notify")
        .tag("status", status)
        .register(registry));
  }
}
