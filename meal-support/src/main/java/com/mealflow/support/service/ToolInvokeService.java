package com.mealflow.support.service;

import com.mealflow.common.api.ErrorCode;
import com.mealflow.common.exception.BizException;
import com.mealflow.support.client.MockToolClient;
import com.mealflow.support.client.RealToolClient;
import com.mealflow.support.client.ToolClient;
import com.mealflow.support.dto.ToolDefinition;
import com.mealflow.support.dto.ToolInvokeRequest;
import com.mealflow.support.dto.ToolInvokeResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Orchestrates tool execution: internal token -> server-side session -> role whitelist -> required
 * params -> forged identity rejection -> mock/real client -> wrapped response.
 */
@Service
public class ToolInvokeService {
  private final SessionContextStore sessionStore;
  private final ToolRegistryService registry;
  private final MockToolClient mockToolClient;
  private final RealToolClient realToolClient;
  private final String internalToolToken;
  private final String toolClientMode;

  public ToolInvokeService(SessionContextStore sessionStore, ToolRegistryService registry,
      MockToolClient mockToolClient, RealToolClient realToolClient,
      @Value("${mealflow.support.internal.tool-token:}") String internalToolToken,
      @Value("${mealflow.support.tool-client.mode:mock}") String toolClientMode) {
    this.sessionStore = sessionStore;
    this.registry = registry;
    this.mockToolClient = mockToolClient;
    this.realToolClient = realToolClient;
    this.internalToolToken = internalToolToken;
    this.toolClientMode = toolClientMode;
  }

  public ToolInvokeResponse invoke(String providedToken, ToolInvokeRequest request) {
    verifyInternalToken(providedToken);
    SessionContextStore.SessionContext session = sessionStore.getRequired(request.sessionId());
    ToolDefinition tool = registry.getRequired(request.toolName());
    requireRole(session, tool);
    requireParams(tool, request.arguments());
    rejectForgedIdentity(request.arguments());
    ToolClient client = tool.mockOnly() || "mock".equals(toolClientMode) ? mockToolClient : realToolClient;
    return client.execute(tool.name(), session, request.arguments());
  }

  private void verifyInternalToken(String providedToken) {
    if (internalToolToken == null || internalToolToken.isBlank()) {
      throw new BizException(ErrorCode.FORBIDDEN, "internal tool token is not configured");
    }
    byte[] expected = internalToolToken.getBytes(StandardCharsets.UTF_8);
    byte[] provided = providedToken == null ? new byte[0] : providedToken.getBytes(StandardCharsets.UTF_8);
    if (!MessageDigest.isEqual(expected, provided)) {
      throw new BizException(ErrorCode.FORBIDDEN, "invalid internal tool token");
    }
  }

  private void requireRole(SessionContextStore.SessionContext session, ToolDefinition tool) {
    if (tool.allowedRoles() == null || !tool.allowedRoles().contains(session.role())) {
      throw new BizException(ErrorCode.FORBIDDEN, "role " + session.role() + " is not allowed to use tool "
          + tool.name());
    }
  }

  private void requireParams(ToolDefinition tool, Map<String, Object> arguments) {
    for (String required : tool.requiredParams()) {
      Object value = arguments == null ? null : arguments.get(required);
      if (value == null || String.valueOf(value).isBlank()) {
        throw new BizException(ErrorCode.BAD_REQUEST, "tool " + tool.name() + " requires argument: " + required);
      }
    }
  }

  private void rejectForgedIdentity(Map<String, Object> arguments) {
    if (arguments == null) {
      return;
    }
    for (String forbidden : new String[] {"userId", "role", "merchantId"}) {
      if (arguments.containsKey(forbidden)) {
        throw new BizException(ErrorCode.FORBIDDEN,
            "argument '" + forbidden + "' must not be supplied by the agent");
      }
    }
  }
}
