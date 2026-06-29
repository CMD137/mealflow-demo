package com.mealflow.notify.api;

import jakarta.validation.constraints.NotNull;
import java.util.Map;

public record TemplateMessageRequest(long userId, @NotNull Map<String, String> variables, String targetPhone) {
}
