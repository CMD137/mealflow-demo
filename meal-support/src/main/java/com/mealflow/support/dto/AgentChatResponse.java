package com.mealflow.support.dto;

import java.util.List;

/** Response from the Python agent runtime. */
public record AgentChatResponse(String sessionId, String answer, List<String> usedTools,
    List<Citation> citations, String traceId, String modelName, Long llmElapsedMs, Long toolElapsedMs) {
}
