package com.mealflow.promotion.metrics;

import com.mealflow.promotion.mapper.PromotionMapper;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PromotionBusinessMetrics {
  private static final List<String> CLAIM_RETRY_STATUSES = List.of("PENDING", "RETRY", "REPAIRED", "DEAD");

  public PromotionBusinessMetrics(MeterRegistry registry, PromotionMapper promotionMapper) {
    CLAIM_RETRY_STATUSES.forEach(status -> Gauge.builder("mealflow.voucher.claim.retries", promotionMapper,
            mapper -> mapper.countClaimRetryByStatus(status))
        .description("Voucher claim repair task count by status")
        .tag("service", "promotion")
        .tag("status", status)
        .register(registry));
  }
}
