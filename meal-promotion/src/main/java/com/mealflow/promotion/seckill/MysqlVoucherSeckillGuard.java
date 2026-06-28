package com.mealflow.promotion.seckill;

import com.mealflow.promotion.mapper.PromotionMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "mealflow.promotion", name = "seckill-mode", havingValue = "mysql",
    matchIfMissing = true)
public class MysqlVoucherSeckillGuard implements VoucherSeckillGuard {
  private final PromotionMapper promotionMapper;

  public MysqlVoucherSeckillGuard(PromotionMapper promotionMapper) {
    this.promotionMapper = promotionMapper;
  }

  @Override
  public ClaimResult tryClaim(long userId, long voucherId, int initialStock) {
    if (promotionMapper.countUserVoucher(userId, voucherId) > 0) {
      return ClaimResult.DUPLICATE;
    }
    return promotionMapper.decrementStock(voucherId) == 1 ? ClaimResult.ACCEPTED : ClaimResult.SOLD_OUT;
  }

  @Override
  public void compensate(long userId, long voucherId) {
    promotionMapper.incrementStock(voucherId);
  }
}
