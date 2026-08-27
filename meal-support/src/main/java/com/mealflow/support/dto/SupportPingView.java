package com.mealflow.support.dto;

import java.util.List;

public record SupportPingView(String service, List<ToolDefinition> tools) {
}
