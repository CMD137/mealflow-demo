package com.mealflow.promotion.seckill;

import com.mealflow.promotion.mapper.PromotionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessException;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.stereotype.Component;

@Component
public class VoucherRedisStockInitializer {
  private static final Logger log = LoggerFactory.getLogger(VoucherRedisStockInitializer.class);

  private final PromotionMapper promotionMapper;
  private final VoucherSeckillGuard seckillGuard;

  public VoucherRedisStockInitializer(PromotionMapper promotionMapper, VoucherSeckillGuard seckillGuard) {
    this.promotionMapper = promotionMapper;
    this.seckillGuard = seckillGuard;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void initializeVoucherStock() {
    try {
      promotionMapper.findVouchers().stream()
          // MySQL stock is the remaining stock after settled claims. setIfAbsent preserves a
          // live Redis counter but restores it after cache loss, including for claimed vouchers.
          .forEach(voucher -> seckillGuard.syncStockIfAbsent(voucher.getId(), voucher.getStock()));
    } catch (DataAccessException ex) {
      log.warn("Redis unavailable while initializing voucher stock; seckill will retry when Redis recovers", ex);
    }
  }
}
