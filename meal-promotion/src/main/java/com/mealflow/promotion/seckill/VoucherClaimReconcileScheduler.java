package com.mealflow.promotion.seckill;

import com.mealflow.promotion.PromotionService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "mealflow.promotion.reconcile", name = "enabled", havingValue = "true")
public class VoucherClaimReconcileScheduler {
  private final PromotionService promotionService;

  public VoucherClaimReconcileScheduler(PromotionService promotionService) {
    this.promotionService = promotionService;
  }

  @Scheduled(
      initialDelayString = "${mealflow.promotion.reconcile.initial-delay-ms:15000}",
      fixedDelayString = "${mealflow.promotion.reconcile.fixed-delay-ms:30000}"
  )
  public void reconcile() {
    promotionService.reconcileRedisClaims();
  }
}
