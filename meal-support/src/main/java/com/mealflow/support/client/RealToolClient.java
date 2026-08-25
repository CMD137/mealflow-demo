package com.mealflow.support.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mealflow.common.api.ErrorCode;
import com.mealflow.common.exception.BizException;
import com.mealflow.support.dto.ToolInvokeResponse;
import com.mealflow.support.service.SessionContextStore.SessionContext;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Real mode tool execution: talks directly to business services (bypassing the gateway, exactly like
 * other internal callers) and always forwards the session user id in X-User-Id.
 */
@Component
public class RealToolClient implements ToolClient {
  private final RestClient restClient;
  private final ObjectMapper objectMapper;
  private final String orderBaseUrl;
  private final String queueBaseUrl;
  private final String promotionBaseUrl;
  private final String notifyBaseUrl;
  private final String merchantBaseUrl;
  private final String catalogBaseUrl;

  public RealToolClient(RestClient restClient, ObjectMapper objectMapper,
      @Value("${mealflow.support.tool-client.order.base-url:http://localhost:8105}") String orderBaseUrl,
      @Value("${mealflow.support.tool-client.queue.base-url:http://localhost:8106}") String queueBaseUrl,
      @Value("${mealflow.support.tool-client.promotion.base-url:http://localhost:8107}") String promotionBaseUrl,
      @Value("${mealflow.support.tool-client.notify.base-url:http://localhost:8110}") String notifyBaseUrl,
      @Value("${mealflow.support.tool-client.merchant.base-url:http://localhost:8102}") String merchantBaseUrl,
      @Value("${mealflow.support.tool-client.catalog.base-url:http://localhost:8103}") String catalogBaseUrl) {
    this.restClient = restClient;
    this.objectMapper = objectMapper;
    this.orderBaseUrl = orderBaseUrl;
    this.queueBaseUrl = queueBaseUrl;
    this.promotionBaseUrl = promotionBaseUrl;
    this.notifyBaseUrl = notifyBaseUrl;
    this.merchantBaseUrl = merchantBaseUrl;
    this.catalogBaseUrl = catalogBaseUrl;
  }

  @Override
  public ToolInvokeResponse execute(String toolName, SessionContext session, Map<String, Object> arguments) {
    try {
      return switch (toolName) {
        case "get_order_detail" -> orderDetail(session, arguments);
        case "get_user_orders" -> userOrders(session);
        case "get_queue_status" -> queueStatus(session);
        case "get_voucher_wallet" -> voucherWallet(session);
        case "get_voucher_claim_status" -> voucherClaimStatus(session, arguments);
        case "get_my_notifications" -> myNotifications(session);
        case "get_merchant_menu" -> merchantMenu(arguments);
        default -> ToolInvokeResponse.error(toolName, "UNSUPPORTED_TOOL",
            "real client does not support tool: " + toolName);
      };
    } catch (BizException ex) {
      return ToolInvokeResponse.error(toolName, ex.errorCode().code(), ex.getMessage());
    } catch (RuntimeException ex) {
      String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
      return ToolInvokeResponse.error(toolName, ErrorCode.SYSTEM_ERROR.code(), message);
    }
  }

  private ToolInvokeResponse orderDetail(SessionContext session, Map<String, Object> arguments) {
    long orderId = longArg(arguments, "orderId");
    JsonNode order = get(orderBaseUrl + "/orders/" + orderId, session.userId());
    if (order.path("userId").asLong() != session.userId()) {
      throw new BizException(ErrorCode.FORBIDDEN, "order does not belong to current user");
    }
    return ToolInvokeResponse.ok("get_order_detail", order);
  }

  private ToolInvokeResponse userOrders(SessionContext session) {
    JsonNode orders = get(orderBaseUrl + "/orders", session.userId());
    return ToolInvokeResponse.ok("get_user_orders", orders);
  }

  private ToolInvokeResponse queueStatus(SessionContext session) {
    JsonNode orders = get(orderBaseUrl + "/orders", session.userId());
    JsonNode matched = null;
    if (orders.isArray()) {
      for (JsonNode order : orders) {
        if (!order.path("queueTicketId").isNull() && order.path("queueTicketId").asLong() > 0) {
          matched = order;
          break;
        }
      }
    }
    if (matched == null) {
      return ToolInvokeResponse.ok("get_queue_status", Map.of("hasTicket", false,
          "message", "当前用户没有排队中的订单"));
    }
    long ticketId = matched.path("queueTicketId").asLong();
    JsonNode ticket = get(queueBaseUrl + "/queue/tickets/" + ticketId, session.userId());
    return ToolInvokeResponse.ok("get_queue_status", Map.of(
        "hasTicket", true,
        "orderId", matched.path("orderId").asLong(),
        "queueTicketId", ticketId,
        "orderStatus", matched.path("status").asText(),
        "ticket", ticket));
  }

  private ToolInvokeResponse voucherWallet(SessionContext session) {
    JsonNode wallet = get(promotionBaseUrl + "/vouchers/wallet", session.userId());
    return ToolInvokeResponse.ok("get_voucher_wallet", wallet);
  }

  private ToolInvokeResponse voucherClaimStatus(SessionContext session, Map<String, Object> arguments) {
    long voucherId = longArg(arguments, "voucherId");
    JsonNode claim = get(promotionBaseUrl + "/vouchers/" + voucherId + "/claims/me", session.userId());
    return ToolInvokeResponse.ok("get_voucher_claim_status", claim);
  }

  private ToolInvokeResponse myNotifications(SessionContext session) {
    JsonNode messages = get(notifyBaseUrl + "/notify/messages", session.userId());
    return ToolInvokeResponse.ok("get_my_notifications", messages);
  }

  private ToolInvokeResponse merchantMenu(Map<String, Object> arguments) {
    long merchantId = longArg(arguments, "merchantId");
    JsonNode merchant = get(merchantBaseUrl + "/merchants/" + merchantId, null);
    JsonNode skus = get(catalogBaseUrl + "/catalog/merchants/" + merchantId + "/skus", null);
    return ToolInvokeResponse.ok("get_merchant_menu", Map.of("merchant", merchant, "skus", skus));
  }

  private JsonNode get(String url, Long userId) {
    RestClient.RequestHeadersSpec<?> spec = restClient.get().uri(url)
        .accept(MediaType.APPLICATION_JSON);
    if (userId != null) {
      spec = spec.header("X-User-Id", Long.toString(userId));
    }
    ApiResult result = spec.retrieve().body(ApiResult.class);
    if (result == null || !result.success()) {
      String message = result == null ? "empty response" : result.message();
      throw new BizException(ErrorCode.SYSTEM_ERROR, message == null ? "downstream call failed" : message);
    }
    return objectMapper.valueToTree(result.data());
  }

  private long longArg(Map<String, Object> arguments, String name) {
    Object value = arguments.get(name);
    if (value == null) {
      throw new BizException(ErrorCode.BAD_REQUEST, "missing required argument: " + name);
    }
    if (value instanceof Number number) {
      return number.longValue();
    }
    return Long.parseLong(String.valueOf(value));
  }

  private record ApiResult(boolean success, String code, String message, Object data) {
  }
}
