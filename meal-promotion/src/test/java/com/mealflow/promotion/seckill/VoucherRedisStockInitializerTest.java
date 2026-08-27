package com.mealflow.promotion.seckill;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.mealflow.promotion.mapper.PromotionMapper;
import com.mealflow.promotion.mapper.VoucherRow;
import java.util.List;
import org.springframework.dao.DataAccessResourceFailureException;
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
  void normalRestartDoesNotInitializeOrOverwriteRedisStock() {
    new VoucherRedisStockInitializer(promotionMapper, seckillGuard, false).initializeVoucherStock();

    verify(seckillGuard, never()).syncStockIfAbsent(org.mockito.ArgumentMatchers.anyLong(),
        org.mockito.ArgumentMatchers.anyInt());
    verify(seckillGuard, never()).markStateInitialized();
  }

  @Test
  void controlledBootstrapInitializesAllStocksBeforeWritingMarker() {
    VoucherRow voucher = new VoucherRow();
    voucher.setId(1000L);
    voucher.setStock(73);
    when(promotionMapper.findVouchers()).thenReturn(List.of(voucher));
    when(seckillGuard.isStateInitialized()).thenReturn(false);

    new VoucherRedisStockInitializer(promotionMapper, seckillGuard, true).initializeVoucherStock();

    org.mockito.InOrder order = org.mockito.Mockito.inOrder(seckillGuard);
    order.verify(seckillGuard).syncStockIfAbsent(1000L, 73);
    order.verify(seckillGuard).markStateInitialized();
  }

  @Test
  void failedBootstrapNeverWritesStateMarker() {
    VoucherRow voucher = new VoucherRow();
    voucher.setId(1000L);
    voucher.setStock(73);
    when(promotionMapper.findVouchers()).thenReturn(List.of(voucher));
    when(seckillGuard.isStateInitialized()).thenReturn(false);
    doThrow(new DataAccessResourceFailureException("redis unavailable"))
        .when(seckillGuard).syncStockIfAbsent(1000L, 73);

    new VoucherRedisStockInitializer(promotionMapper, seckillGuard, true).initializeVoucherStock();

    verify(seckillGuard, never()).markStateInitialized();
  }

  @Test
  void bootstrapDoesNotRunWhenMarkerAlreadyExists() {
    when(seckillGuard.isStateInitialized()).thenReturn(true);

    new VoucherRedisStockInitializer(promotionMapper, seckillGuard, true).initializeVoucherStock();

    verify(seckillGuard, never()).syncStockIfAbsent(org.mockito.ArgumentMatchers.anyLong(),
        org.mockito.ArgumentMatchers.anyInt());
    verify(seckillGuard, never()).markStateInitialized();
  }
}
