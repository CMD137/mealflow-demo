package com.mealflow.promotion.seckill;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mealflow.promotion.mapper.PromotionMapper;
import com.mealflow.promotion.mapper.VoucherRow;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VoucherRedisStockInitializerTest {
  @Mock
  private PromotionMapper promotionMapper;

  @Mock
  private VoucherSeckillGuard seckillGuard;

  @Test
  void restoresCacheForVouchersThatAlreadyHaveClaims() {
    VoucherRow voucher = new VoucherRow();
    voucher.setId(1000L);
    voucher.setStock(73);
    when(promotionMapper.findVouchers()).thenReturn(List.of(voucher));

    new VoucherRedisStockInitializer(promotionMapper, seckillGuard).initializeVoucherStock();

    verify(seckillGuard).syncStockIfAbsent(1000L, 73);
  }
}
