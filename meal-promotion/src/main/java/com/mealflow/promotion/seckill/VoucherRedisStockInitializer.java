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
  public void initializeUnclaimedVoucherStock() {
    try {
      promotionMapper.findVouchers().stream()
          .filter(voucher -> promotionMapper.countVoucherClaims(voucher.getId()) == 0)
          .forEach(voucher -> seckillGuard.syncStockIfAbsent(voucher.getId(), voucher.getStock()));
    } catch (DataAccessException ex) {
      log.warn("Redis unavailable while initializing unclaimed voucher stock; seckill will return NOT_READY", ex);
    }
  }
}
