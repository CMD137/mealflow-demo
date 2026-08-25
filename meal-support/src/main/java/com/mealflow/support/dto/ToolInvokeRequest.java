package com.mealflow.support.dto;

import java.util.Map;

/** Tool execution request from the Python agent runtime. Session identity is authoritative server-side. */
public record ToolInvokeRequest(String sessionId, String toolName, Map<String, Object> arguments) {
}
