package com.mealflow.order.api;

import java.util.List;

public record OrderView(
    long orderId,
    long userId,
    long merchantId,
    String status,
    Long queueTicketId,
    long capacityTokenId,
    long payOrderId,
    int amountCent,
    List<OrderItemSnapshot> items
) {
}
