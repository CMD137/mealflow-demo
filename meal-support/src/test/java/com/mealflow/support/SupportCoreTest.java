package com.mealflow.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mealflow.common.exception.BizException;
import com.mealflow.support.client.MockToolClient;
import com.mealflow.support.client.RealToolClient;
import com.mealflow.support.dto.ToolInvokeRequest;
import com.mealflow.support.dto.ToolInvokeResponse;
import com.mealflow.support.service.SessionContextStore;
import com.mealflow.support.service.SessionContextStore.SessionContext;
import com.mealflow.support.service.ToolInvokeService;
import com.mealflow.support.service.ToolRegistryService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class SupportCoreTest {
  private SessionContextStore sessionStore;
  private ToolInvokeService toolInvokeService;
  private SessionContext customerSession;

  @BeforeEach
  void setUp() {
    sessionStore = new SessionContextStore(3600);
    ObjectMapper objectMapper = new ObjectMapper();
    RealToolClient realToolClient = new RealToolClient(RestClient.create(), objectMapper,
        "http://localhost:8105", "http://localhost:8106", "http://localhost:8107",
        "http://localhost:8110", "http://localhost:8102", "http://localhost:8103");
    toolInvokeService = new ToolInvokeService(sessionStore, new ToolRegistryService(),
        new MockToolClient(), realToolClient, "secret-token", "mock");
    customerSession = sessionStore.create(100L, "CUSTOMER", List.of("ORDER_WRITE"), "trace-1", "test");
  }

  @Test
  void sessionStoreCreatesAndReturnsContext() {
    SessionContext context = sessionStore.getRequired(customerSession.sessionId());
    assertEquals(100L, context.userId());
    assertEquals("CUSTOMER", context.role());
  }

  @Test
  void sessionStoreRejectsForeignUser() {
    BizException ex = assertThrows(BizException.class,
        () -> sessionStore.ensureOwnedBy(customerSession,
            new com.mealflow.support.dto.CurrentUserContext(200L, "CUSTOMER", List.of(), "trace-2")));
    assertEquals("FORBIDDEN", ex.errorCode().code());
  }

  @Test
  void sessionStoreExpiresAfterTtl() throws InterruptedException {
    SessionContextStore shortLived = new SessionContextStore(1);
    SessionContext context = shortLived.create(1L, "CUSTOMER", List.of(), "t", "test");
    Thread.sleep(1200);
    BizException ex = assertThrows(BizException.class, () -> shortLived.getRequired(context.sessionId()));
    assertEquals("NOT_FOUND", ex.errorCode().code());
  }

  @Test
  void invokeRequiresInternalToken() {
    BizException ex = assertThrows(BizException.class,
        () -> toolInvokeService.invoke("wrong-token",
            new ToolInvokeRequest(customerSession.sessionId(), "get_user_orders", Map.of())));
    assertEquals("FORBIDDEN", ex.errorCode().code());
  }

  @Test
  void invokeMockModeReturnsFixedData() {
    ToolInvokeResponse response = toolInvokeService.invoke("secret-token",
        new ToolInvokeRequest(customerSession.sessionId(), "get_user_orders", Map.of()));
    assertTrue(response.success());
    assertNotNull(response.data());
  }

  @Test
  void invokeRejectsForgedIdentityArguments() {
    BizException ex = assertThrows(BizException.class,
        () -> toolInvokeService.invoke("secret-token",
            new ToolInvokeRequest(customerSession.sessionId(), "get_order_detail",
                Map.of("orderId", 1L, "userId", 100L))));
    assertEquals("FORBIDDEN", ex.errorCode().code());
  }

  @Test
  void invokeRejectsUnknownTool() {
    BizException ex = assertThrows(BizException.class,
        () -> toolInvokeService.invoke("secret-token",
            new ToolInvokeRequest(customerSession.sessionId(), "not_a_tool", Map.of())));
    assertEquals("NOT_FOUND", ex.errorCode().code());
  }

  @Test
  void invokeRejectsMissingRequiredParam() {
    BizException ex = assertThrows(BizException.class,
        () -> toolInvokeService.invoke("secret-token",
            new ToolInvokeRequest(customerSession.sessionId(), "get_order_detail", Map.of())));
    assertEquals("BAD_REQUEST", ex.errorCode().code());
  }

  @Test
  void registryExposesNineTools() {
    assertEquals(9, new ToolRegistryService().all().size());
    assertFalse(new ToolRegistryService().all().stream().anyMatch(tool -> tool.name().startsWith("internal_")));
  }
}
