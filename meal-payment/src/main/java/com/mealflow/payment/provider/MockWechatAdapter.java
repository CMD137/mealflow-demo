package com.mealflow.payment.provider;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MockWechatAdapter implements PaymentProviderPort {
  private final String baseUrl;

  public MockWechatAdapter(@Value("${mealflow.payment.mock-wechat.base-url:http://host.docker.internal:9000}") String baseUrl) {
    this.baseUrl = baseUrl.replaceAll("/+$", "");
  }

  @Override public String code() { return "mock-wechat"; }
  @Override public String checkoutUrl(long payOrderId, int amountCent) { return baseUrl + "/cashier/" + payOrderId; }
  @Override public boolean verifyCallback(java.util.Map<String, String> parameters) { return true; }
  @Override public RefundResult refund(String merchantOrderNo, String refundRequestNo, int amountCent) {
    return new RefundResult(true, false, "MOCK-" + merchantOrderNo, refundRequestNo, "mock refund success", "{}");
  }
  @Override public RefundResult queryRefund(String merchantOrderNo, String refundRequestNo) {
    return new RefundResult(true, false, "MOCK-" + merchantOrderNo, refundRequestNo, "mock refund success", "{}");
  }
}
