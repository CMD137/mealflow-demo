package com.mealflow.promotion.seckill;

import java.util.Set;

public interface VoucherSeckillGuard {
  ClaimResult tryClaim(long userId, long voucherId, long nextRetryTime);

  void compensate(long userId, long voucherId);

  void complete(long userId, long voucherId);

  Set<Long> findDuePending(long voucherId, long now, int limit);

  void delayPending(long userId, long voucherId, long nextRetryTime);

  boolean isClaimed(long userId, long voucherId);

  boolean isPending(long userId, long voucherId);

  long pendingCount(long voucherId);

  boolean isStateInitialized();

  void markStateInitialized();

  default void syncStock(long voucherId, int stock) {
  }

  default boolean syncStockIfAbsent(long voucherId, int stock) {
    return false;
  }

  enum ClaimResult {
    ACCEPTED, SOLD_OUT, DUPLICATE, STOCK_MISSING
  }
}
