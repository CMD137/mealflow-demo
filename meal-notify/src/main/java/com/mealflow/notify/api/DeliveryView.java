package com.mealflow.notify.api;

import java.time.LocalDateTime;

public record DeliveryView(long deliveryId, long messageId, long userId, String channel, String target, String status,
    String content, LocalDateTime createTime) {
}
