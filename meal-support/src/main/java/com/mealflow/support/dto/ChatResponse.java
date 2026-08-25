package com.mealflow.support.dto;

import java.util.List;

public record ChatResponse(String sessionId, String answer, List<String> usedTools, String traceId,
    List<Citation> citations) {
}
