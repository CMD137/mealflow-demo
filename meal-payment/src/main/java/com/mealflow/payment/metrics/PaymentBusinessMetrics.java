package com.mealflow.payment.metrics;

import com.mealflow.payment.mapper.LocalEventMapper;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PaymentBusinessMetrics {
  private static final List<String> OUTBOX_STATUSES = List.of("NEW", "SENDING", "SENT", "FAILED");

  public PaymentBusinessMetrics(MeterRegistry registry, LocalEventMapper localEventMapper) {
    OUTBOX_STATUSES.forEach(status -> Gauge.builder("mealflow.outbox.events", localEventMapper,
            mapper -> mapper.countByStatus(status))
        .description("Payment outbox event count by status")
        .tag("service", "payment")
        .tag("status", status)
        .register(registry));
  }
}
