package com.mealflow.support.client;

import com.mealflow.support.dto.ToolInvokeResponse;
import com.mealflow.support.service.SessionContextStore.SessionContext;
import java.util.Map;

/** Executes a tool for the session principal. Implementations are mock or real. */
public interface ToolClient {
  ToolInvokeResponse execute(String toolName, SessionContext session, Map<String, Object> arguments);
}
