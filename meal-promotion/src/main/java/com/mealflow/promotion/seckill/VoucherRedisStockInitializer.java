package com.mealflow.promotion.seckill;

import com.mealflow.promotion.mapper.PromotionMapper;
import com.mealflow.promotion.mapper.UserVoucherRow;
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
      boolean initialized = seckillGuard.isStateInitialized();
      if (!initialized) {
        promotionMapper.findVouchers().forEach(voucher ->
            // SETNX avoids overwriting state that appeared while this process is starting.
            seckillGuard.syncStockIfAbsent(voucher.getId(), voucher.getStock()));
      }

      // user_voucher is the durable fact that a user has already received the voucher.
      // Replaying SADD is idempotent and is safe even when the marker already exists.
      for (UserVoucherRow userVoucher : promotionMapper.findSeckillUserVouchers()) {
        seckillGuard.recordClaimed(userVoucher.getUserId(), userVoucher.getVoucherId());
      }

      if (!initialized) {
        // Write the marker last. Any initialization failure leaves the system fail-closed.
        seckillGuard.markStateInitialized();
      } else {
        log.info("Reconciled claimed seckill users without overwriting live Redis stock");
      }
    } catch (DataAccessException ex) {
      log.warn("Controlled seckill bootstrap or claimed-user reconciliation did not complete", ex);
    }
  }
}
