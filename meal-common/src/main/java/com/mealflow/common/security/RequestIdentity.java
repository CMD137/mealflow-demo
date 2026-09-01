package com.mealflow.common.security;

import com.mealflow.common.api.ErrorCode;
import com.mealflow.common.exception.BizException;

public final class RequestIdentity {
  private RequestIdentity() {
  }

  public static long requireUser(Long userId) {
    return require(userId, "user identity missing");
  }

  public static long requireMerchant(Long merchantId) {
    return require(merchantId, "merchant identity missing");
  }

  public static void requireMerchant(long expectedMerchantId, Long actualMerchantId) {
    if (requireMerchant(actualMerchantId) != expectedMerchantId) {
      throw new BizException(ErrorCode.FORBIDDEN, "merchant resource does not belong to current principal");
    }
  }

  /** The role header is injected and HMAC-signed by the gateway; never accept it from an unsigned caller. */
  public static void requireRole(String expectedRole, String actualRole) {
    if (!expectedRole.equals(actualRole)) {
      throw new BizException(ErrorCode.FORBIDDEN, "role is not allowed for this resource");
    }
  }

  private static long require(Long value, String message) {
    if (value == null || value <= 0) {
      throw new BizException(ErrorCode.UNAUTHORIZED, message);
    }
    return value;
  }
}
