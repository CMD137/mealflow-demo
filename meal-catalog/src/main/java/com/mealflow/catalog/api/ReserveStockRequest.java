package com.mealflow.catalog.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;

public record ReserveStockRequest(
    @NotBlank String requestId,
    long userId,
    long merchantId,
    Long ticketId,
    Long orderId,
    @Valid List<OrderSkuItem> items,
    @NotNull LocalDateTime expireTime
) {
}
