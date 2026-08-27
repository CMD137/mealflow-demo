package com.mealflow.support.service;

import com.mealflow.common.api.ErrorCode;
import com.mealflow.common.exception.BizException;
import com.mealflow.support.dto.ToolDefinition;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Authoritative registry of the tools exposed to the agent. Tool names express customer-service
 * semantics only and never leak internal service details.
 */
@Component
public class ToolRegistryService {
  private static final List<String> ALL_AUTHENTICATED =
      List.of("CUSTOMER", "MERCHANT_ADMIN", "STORE_STAFF");

  private final Map<String, ToolDefinition> tools = new LinkedHashMap<>();

  public ToolRegistryService() {
    register("query_order_status",
        "按订单号查询订单状态（离线演示数据，非实时）",
        ALL_AUTHENTICATED, List.of("orderNo"), true);
    register("query_user_spending",
        "查询用户在某时间段内的消费统计（离线演示数据，非实时）",
        ALL_AUTHENTICATED, List.of("period"), true);
    register("get_order_detail",
        "按 orderId 查询当前用户订单详情（含状态、金额、商品明细）",
        ALL_AUTHENTICATED, List.of("orderId"), false);
    register("get_user_orders",
        "查询当前用户全部订单列表",
        ALL_AUTHENTICATED, List.of(), false);
    register("get_queue_status",
        "查询当前用户排队状态（排第几、预计等待时长、排队票据状态）",
        ALL_AUTHENTICATED, List.of(), false);
    register("get_voucher_wallet",
        "查询当前用户优惠券包（可用/已锁定/已核销）",
        ALL_AUTHENTICATED, List.of(), false);
    register("get_voucher_claim_status",
        "按 voucherId 查询当前用户的秒杀券领取状态",
        ALL_AUTHENTICATED, List.of("voucherId"), false);
    register("get_my_notifications",
        "查询当前用户收到的站内通知消息",
        ALL_AUTHENTICATED, List.of(), false);
    register("get_merchant_menu",
        "按 merchantId 查询商家信息与菜品/SKU 列表（公开数据）",
        ALL_AUTHENTICATED, List.of("merchantId"), false);
  }

  private void register(String name, String description, List<String> allowedRoles,
      List<String> requiredParams, boolean mockOnly) {
    tools.put(name, new ToolDefinition(name, description, allowedRoles, requiredParams, mockOnly));
  }

  public ToolDefinition getRequired(String name) {
    ToolDefinition tool = tools.get(name);
    if (tool == null) {
      throw new BizException(ErrorCode.NOT_FOUND, "unsupported tool: " + name);
    }
    return tool;
  }

  public List<ToolDefinition> all() {
    return List.copyOf(tools.values());
  }
}
