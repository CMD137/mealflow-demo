package com.mealflow.promotion.seckill;

import com.mealflow.promotion.mapper.PromotionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.stereotype.Component;

@Component
public class VoucherRedisStockInitializer {
  private static final Logger log = LoggerFactory.getLogger(VoucherRedisStockInitializer.class);

  private final PromotionMapper promotionMapper;
  private final VoucherSeckillGuard seckillGuard;
  private final boolean bootstrapEnabled;

  public VoucherRedisStockInitializer(PromotionMapper promotionMapper, VoucherSeckillGuard seckillGuard,
      @Value("${mealflow.promotion.seckill.bootstrap.enabled:false}") boolean bootstrapEnabled) {
    this.promotionMapper = promotionMapper;
    this.seckillGuard = seckillGuard;
    this.bootstrapEnabled = bootstrapEnabled;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void initializeVoucherStock() {
    if (!bootstrapEnabled) {
      return;
    }
    try {
      if (seckillGuard.isStateInitialized()) {
        log.warn("Seckill bootstrap is enabled but Redis state marker already exists; skip to avoid overwriting live state");
        return;
      }
      promotionMapper.findVouchers().stream()
          // Bootstrap is an explicitly controlled operation. SETNX avoids overwriting any
          // state that appeared while this process is starting.
          .forEach(voucher -> seckillGuard.syncStockIfAbsent(voucher.getId(), voucher.getStock()));
      // Write the marker last. Any initialization failure leaves the system fail-closed.
      seckillGuard.markStateInitialized();
    } catch (DataAccessException ex) {
      log.warn("Controlled seckill bootstrap did not complete; marker was not written", ex);
    }
  }
}
