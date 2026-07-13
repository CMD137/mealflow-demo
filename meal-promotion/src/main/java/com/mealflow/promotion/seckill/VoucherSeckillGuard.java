package com.mealflow.promotion.seckill;

import java.util.Set;

public interface VoucherSeckillGuard {
  ClaimResult tryClaim(long userId, long voucherId, int initialStock);

  void compensate(long userId, long voucherId);

  default int remainingStock(long voucherId, int databaseStock) {
    return databaseStock;
  }

  default void syncStock(long voucherId, int stock) {
  }

  default Set<Long> claimedUsers(long voucherId) {
    return Set.of();
  }

  enum ClaimResult {
    ACCEPTED, SOLD_OUT, DUPLICATE
  }
}
