package com.mealflow.notify.api;

import jakarta.validation.constraints.NotBlank;

public record PushMessageRequest(long userId, @NotBlank String bizType, @NotBlank String content) {
}
