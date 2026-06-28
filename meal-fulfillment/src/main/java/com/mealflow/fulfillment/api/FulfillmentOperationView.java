package com.mealflow.fulfillment.api;

import java.time.LocalDateTime;

public record FulfillmentOperationView(
    long id,
    String requestId,
    long orderId,
    String action,
    String status,
    String message,
    LocalDateTime createTime
) {
}
