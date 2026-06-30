package com.mealflow.order.api;

import java.time.LocalDateTime;

public record AdminOrderQuery(Long merchantId, Long userId, String status, LocalDateTime beginTime,
    LocalDateTime endTime) {
}
