package com.mealflow.app.catalog;

import java.time.LocalDateTime;
import java.util.List;

public record ReserveStockCommand(
    String requestId,
    long userId,
    long merchantId,
    List<OrderSkuItem> items,
    LocalDateTime expireTime
) {
}
