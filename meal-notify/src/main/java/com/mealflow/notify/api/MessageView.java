package com.mealflow.notify.api;

import java.time.LocalDateTime;

public record MessageView(long messageId, long userId, String recipientType, long recipientId, String bizType,
                          String content, LocalDateTime createTime) {
}
