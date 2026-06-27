package com.mealflow.queue.api;

import java.util.List;
import java.util.Map;

public record QueueTicketSnapshot(
    List<Map<String, Object>> items,
    List<Long> reservationIds,
    Long voucherLockId,
    int totalAmount,
    String remark
) {
}
