package com.mealflow.payment.provider;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradeFastpayRefundQueryRequest;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.alipay.api.response.AlipayTradeFastpayRefundQueryResponse;
import com.alipay.api.response.AlipayTradeRefundResponse;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AlipaySandboxAdapter implements PaymentProviderPort {
  private static final String GATEWAY = "https://openapi-sandbox.dl.alipaydev.com/gateway.do";
  private static final String CHARSET = "UTF-8";
  private static final String SIGN_TYPE = "RSA2";

  private final String appId;
  private final String privateKey;
  private final String publicKey;
  private final String notifyUrl;

  public AlipaySandboxAdapter(@Value("${mealflow.payment.alipay.app-id:}") String appId,
      @Value("${mealflow.payment.alipay.private-key:}") String privateKey,
      @Value("${mealflow.payment.alipay.public-key:}") String publicKey,
      @Value("${mealflow.payment.alipay.notify-url:}") String notifyUrl) {
    this.appId = appId;
    this.privateKey = privateKey;
    this.publicKey = publicKey;
    this.notifyUrl = notifyUrl;
  }

  @Override
  public String code() {
    return "alipay-sandbox";
  }

  @Override
  public String checkoutUrl(long payOrderId, int amountCent) {
    requireCheckoutConfiguration();
    AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();
    request.setNotifyUrl(notifyUrl);
    request.setBizContent("{\"out_trade_no\":\"MF" + payOrderId
        + "\",\"product_code\":\"FAST_INSTANT_TRADE_PAY\",\"total_amount\":\""
        + amount(amountCent) + "\",\"subject\":\"MealFlow order " + payOrderId + "\"}");
    try {
      return client().pageExecute(request, "GET").getBody();
    } catch (AlipayApiException ex) {
      throw new IllegalStateException("failed to create alipay checkout request", ex);
    }
  }

  @Override
  public boolean verifyCallback(Map<String, String> parameters) {
    if (!appId.equals(parameters.get("app_id")) || !SIGN_TYPE.equals(parameters.get("sign_type"))
        || publicKey.isBlank()) {
      return false;
    }
    try {
      return AlipaySignature.rsaCheckV1(parameters, publicKey, CHARSET, SIGN_TYPE);
    } catch (AlipayApiException ex) {
      return false;
    }
  }

  @Override
  public RefundResult refund(String merchantOrderNo, String refundRequestNo, int amountCent) {
    requireApiConfiguration();
    AlipayTradeRefundRequest request = new AlipayTradeRefundRequest();
    request.setBizContent("{\"out_trade_no\":\"" + merchantOrderNo + "\",\"refund_amount\":\""
        + amount(amountCent) + "\",\"out_request_no\":\"" + refundRequestNo
        + "\",\"refund_reason\":\"MealFlow order cancellation\"}");
    try {
      AlipayTradeRefundResponse response = client().execute(request);
      boolean apiSuccess = response.isSuccess();
      boolean refunded = apiSuccess && "Y".equals(response.getFundChange());
      return new RefundResult(refunded, apiSuccess || retryable(response.getSubCode()),
          blankToNull(response.getTradeNo()), refundRequestNo, message(response.getMsg(), response.getSubMsg()),
          response.getBody());
    } catch (AlipayApiException ex) {
      throw new IllegalStateException("alipay refund request failed", ex);
    }
  }

  @Override
  public RefundResult queryRefund(String merchantOrderNo, String refundRequestNo) {
    requireApiConfiguration();
    AlipayTradeFastpayRefundQueryRequest request = new AlipayTradeFastpayRefundQueryRequest();
    request.setBizContent("{\"out_trade_no\":\"" + merchantOrderNo + "\",\"out_request_no\":\""
        + refundRequestNo + "\"}");
    try {
      AlipayTradeFastpayRefundQueryResponse response = client().execute(request);
      boolean apiSuccess = response.isSuccess();
      boolean refunded = apiSuccess && ("REFUND_SUCCESS".equals(response.getRefundStatus())
          || blankToNull(response.getRefundAmount()) != null);
      return new RefundResult(refunded, apiSuccess && !refunded, blankToNull(response.getTradeNo()),
          refundRequestNo, message(response.getMsg(), response.getSubMsg()), response.getBody());
    } catch (AlipayApiException ex) {
      throw new IllegalStateException("alipay refund query failed", ex);
    }
  }

  private AlipayClient client() {
    return new DefaultAlipayClient(GATEWAY, appId, privateKey, "json", CHARSET, publicKey, SIGN_TYPE);
  }

  private void requireCheckoutConfiguration() {
    if (appId.isBlank() || privateKey.isBlank() || notifyUrl.isBlank()) {
      throw new IllegalStateException("alipay sandbox credentials or callback URL are missing");
    }
  }

  private void requireApiConfiguration() {
    if (appId.isBlank() || privateKey.isBlank() || publicKey.isBlank()) {
      throw new IllegalStateException("alipay sandbox credentials are missing");
    }
  }

  private boolean retryable(String subCode) {
    return subCode == null || subCode.isBlank() || subCode.contains("SYSTEM_ERROR")
        || subCode.contains("ACQ.SYSTEM_ERROR");
  }

  private String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }

  private String message(String message, String subMessage) {
    if (subMessage == null || subMessage.isBlank()) {
      return message;
    }
    return message == null || message.isBlank() ? subMessage : message + ": " + subMessage;
  }

  private String amount(int amountCent) {
    return String.format(java.util.Locale.ROOT, "%.2f", amountCent / 100.0);
  }
}
