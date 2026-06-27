package com.mealflow.app.queue;

import com.mealflow.app.catalog.OrderItemSnapshot;
import java.util.List;

public record QueueTicketSnapshot(
    List<OrderItemSnapshot> items,
    List<Long> reservationIds,
    Long voucherLockId,
    int totalAmount,
    String remark
) {
}
