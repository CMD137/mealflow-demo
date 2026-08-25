package com.mealflow.support.dto;

import java.util.List;

/** Request forwarded to the Python agent runtime. Identity fields come from the server-side session. */
public record AgentChatRequest(String sessionId, String message, String traceId, long userId, String role,
    List<String> permissions, String channel) {
}
