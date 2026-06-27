package com.mealflow.order.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record SubmitOrderRequest(
    @NotBlank String requestId,
    @NotNull Long merchantId,
    Long addressId,
    List<Long> cartItemIds,
    @Valid List<OrderSkuItem> items,
    Long userVoucherId,
    String remark
) {
}
