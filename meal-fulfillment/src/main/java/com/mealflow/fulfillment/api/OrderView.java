package com.mealflow.fulfillment.api;

import java.util.List;
import java.util.Map;

public record OrderView(
    long orderId,
    long userId,
    long merchantId,
    String status,
    Long queueTicketId,
    long capacityTokenId,
    long payOrderId,
    int amountCent,
    List<Map<String, Object>> items
) {
}
