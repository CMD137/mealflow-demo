package com.mealflow.common.api;

public enum ErrorCode {
  BAD_REQUEST("BAD_REQUEST", "请求参数错误"),
  UNAUTHORIZED("UNAUTHORIZED", "未认证或登录已过期"),
  FORBIDDEN("FORBIDDEN", "无权访问该资源"),
  NOT_FOUND("NOT_FOUND", "资源不存在"),
  IDEMPOTENT_PROCESSING("IDEMPOTENT_PROCESSING", "请求正在处理中"),
  ILLEGAL_STATUS("ILLEGAL_STATUS", "状态不允许流转"),
  STOCK_NOT_ENOUGH("STOCK_NOT_ENOUGH", "库存不足"),
  VOUCHER_UNAVAILABLE("VOUCHER_UNAVAILABLE", "优惠券不可用"),
  SOLD_OUT("SOLD_OUT", "已抢完"),
  DUPLICATE("DUPLICATE", "重复操作"),
  CART_MERCHANT_CONFLICT("CART_MERCHANT_CONFLICT", "购物车已有其他商户商品"),
  IDEMPOTENCY_KEY_REUSED("IDEMPOTENCY_KEY_REUSED", "幂等键与请求内容不匹配"),
  SYSTEM_ERROR("SYSTEM_ERROR", "系统异常");

  private final String code;
  private final String message;

  ErrorCode(String code, String message) {
    this.code = code;
    this.message = message;
  }

  public String code() {
    return code;
  }

  public String message() {
    return message;
  }
}
