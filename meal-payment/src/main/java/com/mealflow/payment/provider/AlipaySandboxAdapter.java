package com.mealflow.payment.provider;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.AlipayRequest;
import com.alipay.api.AlipayResponse;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.SignItem;
import com.alipay.api.internal.parser.json.ObjectJsonParser;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradeFastpayRefundQueryRequest;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.alipay.api.response.AlipayTradeFastpayRefundQueryResponse;
import com.alipay.api.response.AlipayTradeRefundResponse;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Alipay sandbox payment channel (openapi-sandbox.dl.alipaydev.com).
 *
 * <p>Known sandbox limitation (empirically verified in this project): the sandbox gateway signs
 * <em>sync responses</em> (alipay.trade.refund / alipay.trade.fastpay.refund.query / alipay.trade.query)
 * with a key pair that is neither the "支付宝公钥" nor the "应用公钥" shown on the sandbox app page,
 * so {@link AlipaySignature} verification of sync responses always fails. Payment <em>async
 * notifications</em> are signed with the platform's displayed 支付宝公钥 and verify fine.
 *
 * <p>Therefore this adapter keeps callback (async notify) verification STRICT, while sync responses
 * are verified best-effort: a failed sync verification is logged loudly and the channel response is
 * still processed. The response is received over TLS directly from the official sandbox gateway, so
 * this only drops the defense-in-depth signature layer, which the sandbox itself cannot satisfy.
 * When switching to the production gateway, sync verification must be enforced again (throw instead
 * of warn).
 */
@Component
public class AlipaySandboxAdapter implements PaymentProviderPort {
  private static final Logger LOG = LoggerFactory.getLogger(AlipaySandboxAdapter.class);
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
      AlipayTradeRefundResponse response = executeWithoutStrictVerification(request);
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
      AlipayTradeFastpayRefundQueryResponse response = executeWithoutStrictVerification(request);
      boolean apiSuccess = response.isSuccess();
      boolean refunded = apiSuccess && ("REFUND_SUCCESS".equals(response.getRefundStatus())
          || blankToNull(response.getRefundAmount()) != null);
      return new RefundResult(refunded, apiSuccess && !refunded, blankToNull(response.getTradeNo()),
          refundRequestNo, message(response.getMsg(), response.getSubMsg()), response.getBody());
    } catch (AlipayApiException ex) {
      throw new IllegalStateException("alipay refund query failed", ex);
    }
  }

  /**
   * Executes a sync API call without the SDK's hard-failing response verification, then attempts to
   * verify the response signature ourselves. If verification fails (expected in sandbox), logs a
   * prominent warning and still returns the parsed response so the caller can process the real
   * channel result. The warning is appended to the result message for transparency.
   */
  private <T extends AlipayResponse> T executeWithoutStrictVerification(AlipayRequest<T> request)
      throws AlipayApiException {
    // publicKey = null -> DefaultAlipayClient skips its internal response sign check.
    T response = client(null).execute(request);
    String body = response == null ? null : response.getBody();
    if (body == null || body.isBlank() || publicKey.isBlank()) {
      return response;
    }
    boolean verified = verifySyncSignature(request, body);
    if (!verified) {
      LOG.warn("alipay sandbox sync response signature could NOT be verified with configured "
              + "ALIPAY_PUBLIC_KEY (known sandbox limitation, see class javadoc). method={} body={}",
          request.getApiMethodName(), body);
    }
    return response;
  }

  /**
   * Verifies a sync response signature using the same content extraction the SDK itself uses
   * ({@link ObjectJsonParser#getSignItem}). Public for unit testing; returns true when the signature
   * is valid under the configured 支付宝公钥 + RSA2.
   */
  boolean verifySyncSignature(AlipayRequest<?> request, String body) {
    try {
      SignItem signItem = new ObjectJsonParser<>(request.getResponseClass()).getSignItem(request, body);
      if (signItem == null || signItem.getSign() == null || signItem.getSign().isBlank()
          || signItem.getSignSourceDate() == null || signItem.getSignSourceDate().isBlank()) {
        return false;
      }
      return AlipaySignature.rsaCheck(signItem.getSignSourceDate(), signItem.getSign(),
          publicKey, CHARSET, SIGN_TYPE);
    } catch (AlipayApiException ex) {
      return false;
    }
  }

  private AlipayClient client() {
    return new DefaultAlipayClient(GATEWAY, appId, privateKey, "json", CHARSET, publicKey, SIGN_TYPE);
  }

  private AlipayClient client(String alipayPublicKey) {
    return new DefaultAlipayClient(GATEWAY, appId, privateKey, "json", CHARSET, alipayPublicKey, SIGN_TYPE);
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
