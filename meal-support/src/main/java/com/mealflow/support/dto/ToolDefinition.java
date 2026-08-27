package com.mealflow.support.dto;

import java.util.List;

/** Registry entry describing a tool exposed to the agent. */
public record ToolDefinition(String name, String description, List<String> allowedRoles,
    List<String> requiredParams, boolean mockOnly) {
}
