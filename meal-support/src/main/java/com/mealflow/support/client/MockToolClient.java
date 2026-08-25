package com.mealflow.support.client;

import com.mealflow.support.dto.ToolInvokeResponse;
import com.mealflow.support.service.SessionContextStore.SessionContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Fixed offline data for every tool so the whole chain is demo-able without any backend. */
@Component
public class MockToolClient implements ToolClient {

  @Override
  public ToolInvokeResponse execute(String toolName, SessionContext session, Map<String, Object> arguments) {
    return switch (toolName) {
      case "query_order_status" -> ok(toolName, Map.of(
          "orderNo", arguments.getOrDefault("orderNo", "MF-DEMO-0001"),
          "status", "已支付，等待商家接单",
          "estimatedPickupTime", "预计 12:40 出餐",
          "source", "mock"));
      case "query_user_spending" -> ok(toolName, Map.of(
          "period", arguments.getOrDefault("period", "last-7-days"),
          "orderCount", 3,
          "totalSpentCent", 9860,
          "source", "mock"));
      case "get_order_detail" -> ok(toolName, Map.of(
          "orderId", 1L, "userId", session.userId(), "merchantId", 10L,
          "status", "WAIT_MERCHANT_ACCEPT", "amountCent", 3260,
          "items", List.of(Map.of("skuId", 1L, "skuName", "牛肉饭", "priceCent", 2600, "quantity", 1)),
          "source", "mock"));
      case "get_user_orders" -> ok(toolName, Map.of(
          "orders", List.of(
              Map.of("orderId", 1L, "status", "WAIT_MERCHANT_ACCEPT", "amountCent", 3260, "queueTicketId", 1L),
              Map.of("orderId", 2L, "status", "COMPLETED", "amountCent", 6600, "queueTicketId", 2L)),
          "source", "mock"));
      case "get_queue_status" -> ok(toolName, Map.of(
          "hasTicket", true, "orderId", 1L, "queueTicketId", 1L,
          "ticket", Map.of("ticketId", 1L, "ticketNo", "QT10001", "status", "READY",
              "aheadCount", 0, "estimatedWaitSeconds", 60),
          "source", "mock"));
      case "get_voucher_wallet" -> ok(toolName, Map.of(
          "vouchers", List.of(
              Map.of("id", 1L, "voucherId", 3L, "status", "AVAILABLE"),
              Map.of("id", 2L, "voucherId", 4L, "status", "USED")),
          "source", "mock"));
      case "get_voucher_claim_status" -> ok(toolName, Map.of(
          "voucherId", arguments.getOrDefault("voucherId", 3L),
          "status", "CLAIMED", "claimId", 1L, "userVoucherId", 1L,
          "source", "mock"));
      case "get_my_notifications" -> ok(toolName, Map.of(
          "messages", List.of(
              Map.of("id", 1L, "bizType", "ORDER", "content", "您的订单已支付，等待商家接单。", "createTime", "2026-08-25 12:00:00")),
          "source", "mock"));
      case "get_merchant_menu" -> ok(toolName, Map.of(
          "merchantId", arguments.getOrDefault("merchantId", 10L), "name", "MealFlow 牛肉饭",
          "businessStatus", "OPEN",
          "skus", List.of(
              Map.of("skuId", 1L, "name", "牛肉饭", "priceCent", 2600, "status", "ON_SHELF"),
              Map.of("skuId", 2L, "name", "卤蛋", "priceCent", 200, "status", "ON_SHELF")),
          "source", "mock"));
      default -> ToolInvokeResponse.error(toolName, "UNSUPPORTED_TOOL", "mock client does not know tool: " + toolName);
    };
  }

  private ToolInvokeResponse ok(String toolName, Map<String, Object> data) {
    Map<String, Object> payload = new LinkedHashMap<>(data);
    return ToolInvokeResponse.ok(toolName, payload);
  }
}
