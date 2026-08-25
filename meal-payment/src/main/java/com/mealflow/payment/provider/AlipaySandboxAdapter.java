package com.mealflow.payment.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URLEncoder;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AlipaySandboxAdapter implements PaymentProviderPort {
  private static final String GATEWAY = "https://openapi-sandbox.dl.alipaydev.com/gateway.do";
  private final String appId;
  private final String privateKey;
  private final String publicKey;
  private final String notifyUrl;
  private final ObjectMapper objectMapper;
  private final HttpClient httpClient = HttpClient.newHttpClient();

  public AlipaySandboxAdapter(@Value("${mealflow.payment.alipay.app-id:}") String appId,
      @Value("${mealflow.payment.alipay.private-key:}") String privateKey,
      @Value("${mealflow.payment.alipay.public-key:}") String publicKey,
      @Value("${mealflow.payment.alipay.notify-url:}") String notifyUrl, ObjectMapper objectMapper) {
    this.appId = appId;
    this.privateKey = privateKey;
    this.publicKey = publicKey;
    this.notifyUrl = notifyUrl;
    this.objectMapper = objectMapper;
  }

  @Override public String code() { return "alipay-sandbox"; }

  @Override
  public String checkoutUrl(long payOrderId, int amountCent) {
    if (appId.isBlank() || privateKey.isBlank() || notifyUrl.isBlank()) {
      throw new IllegalStateException("alipay sandbox credentials or callback URL are missing");
    }
    Map<String, String> params = new TreeMap<>();
    params.put("app_id", appId);
    params.put("method", "alipay.trade.page.pay");
    params.put("charset", "utf-8");
    params.put("sign_type", "RSA2");
    params.put("timestamp", OffsetDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
    params.put("version", "1.0");
    params.put("notify_url", notifyUrl);
    params.put("biz_content", "{\"out_trade_no\":\"MF" + payOrderId + "\",\"product_code\":\"FAST_INSTANT_TRADE_PAY\",\"total_amount\":\"" + amount(amountCent) + "\",\"subject\":\"MealFlow order " + payOrderId + "\"}");
    params.put("sign", sign(canonical(params)));
    return GATEWAY + "?" + form(params);
  }

  @Override
  public boolean verifyCallback(Map<String, String> parameters) {
    if (!appId.equals(parameters.get("app_id")) || !"RSA2".equals(parameters.get("sign_type"))) {
      return false;
    }
    String sign = parameters.get("sign");
    if (sign == null || publicKey.isBlank()) {
      return false;
    }
    try {
      Signature verifier = Signature.getInstance("SHA256withRSA");
      verifier.initVerify(publicKey());
      verifier.update(canonical(parameters).getBytes(StandardCharsets.UTF_8));
      return verifier.verify(Base64.getDecoder().decode(sign));
    } catch (Exception ex) {
      return false;
    }
  }

  @Override
  public RefundResult refund(String merchantOrderNo, String refundRequestNo, int amountCent) {
    String bizContent = "{\"out_trade_no\":\"" + merchantOrderNo + "\",\"refund_amount\":\""
        + amount(amountCent) + "\",\"out_request_no\":\"" + refundRequestNo
        + "\",\"refund_reason\":\"MealFlow order cancellation\"}";
    JsonNode response = call("alipay.trade.refund", "alipay_trade_refund_response", bizContent);
    boolean apiSuccess = "10000".equals(response.path("code").asText());
    boolean refunded = apiSuccess && "Y".equals(response.path("fund_change").asText());
    return new RefundResult(refunded, apiSuccess || retryable(response.path("sub_code").asText()),
        text(response, "trade_no"), refundRequestNo, response.path("msg").asText(), response.toString());
  }

  @Override
  public RefundResult queryRefund(String merchantOrderNo, String refundRequestNo) {
    String bizContent = "{\"out_trade_no\":\"" + merchantOrderNo + "\",\"out_request_no\":\""
        + refundRequestNo + "\"}";
    JsonNode response = call("alipay.trade.fastpay.refund.query",
        "alipay_trade_fastpay_refund_query_response", bizContent);
    boolean apiSuccess = "10000".equals(response.path("code").asText());
    boolean refunded = apiSuccess && ("REFUND_SUCCESS".equals(response.path("refund_status").asText())
        || response.hasNonNull("refund_amount"));
    return new RefundResult(refunded, apiSuccess && !refunded, text(response, "trade_no"), refundRequestNo,
        response.path("msg").asText(), response.toString());
  }

  private JsonNode call(String method, String responseField, String bizContent) {
    if (appId.isBlank() || privateKey.isBlank() || publicKey.isBlank()) {
      throw new IllegalStateException("alipay sandbox credentials are missing");
    }
    Map<String, String> params = commonParams(method);
    params.put("biz_content", bizContent);
    params.put("sign", sign(canonical(params)));
    try {
      HttpRequest request = HttpRequest.newBuilder(URI.create(GATEWAY))
          .header("Content-Type", "application/x-www-form-urlencoded;charset=utf-8")
          .POST(HttpRequest.BodyPublishers.ofString(form(params))).build();
      String raw = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).body();
      JsonNode root = objectMapper.readTree(raw);
      JsonNode response = root.path(responseField);
      if (response.isMissingNode()) {
        throw new IllegalStateException("invalid alipay response: " + raw);
      }
      verifyResponse(raw, responseField, root.path("sign").asText());
      return response;
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("alipay request interrupted", ex);
    } catch (Exception ex) {
      throw ex instanceof IllegalStateException state ? state : new IllegalStateException("alipay request failed", ex);
    }
  }

  private Map<String, String> commonParams(String method) {
    Map<String, String> params = new TreeMap<>();
    params.put("app_id", appId);
    params.put("method", method);
    params.put("charset", "utf-8");
    params.put("sign_type", "RSA2");
    params.put("timestamp", OffsetDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
    params.put("version", "1.0");
    return params;
  }

  private void verifyResponse(String raw, String responseField, String responseSign) throws Exception {
    if (responseSign.isBlank()) {
      throw new IllegalStateException("alipay response signature is missing");
    }
    String signedContent = extractJsonObject(raw, "\"" + responseField + "\":");
    Signature verifier = Signature.getInstance("SHA256withRSA");
    verifier.initVerify(publicKey());
    verifier.update(signedContent.getBytes(StandardCharsets.UTF_8));
    if (!verifier.verify(Base64.getDecoder().decode(responseSign))) {
      throw new IllegalStateException("invalid alipay response signature");
    }
  }

  private String extractJsonObject(String raw, String marker) {
    int markerIndex = raw.indexOf(marker);
    int start = markerIndex < 0 ? -1 : raw.indexOf('{', markerIndex + marker.length());
    if (start < 0) {
      throw new IllegalStateException("alipay response body is missing");
    }
    boolean quoted = false;
    boolean escaped = false;
    int depth = 0;
    for (int i = start; i < raw.length(); i++) {
      char ch = raw.charAt(i);
      if (quoted) {
        if (escaped) escaped = false;
        else if (ch == '\\') escaped = true;
        else if (ch == '"') quoted = false;
      } else if (ch == '"') quoted = true;
      else if (ch == '{') depth++;
      else if (ch == '}' && --depth == 0) return raw.substring(start, i + 1);
    }
    throw new IllegalStateException("alipay response body is incomplete");
  }

  private String text(JsonNode node, String field) {
    String value = node.path(field).asText();
    return value.isBlank() ? null : value;
  }

  private boolean retryable(String subCode) {
    return subCode == null || subCode.isBlank() || subCode.contains("SYSTEM_ERROR") || subCode.contains("ACQ.SYSTEM_ERROR");
  }

  private String sign(String content) {
    try {
      Signature signer = Signature.getInstance("SHA256withRSA");
      signer.initSign(privateKey());
      signer.update(content.getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder().encodeToString(signer.sign());
    } catch (Exception ex) {
      throw new IllegalStateException("failed to sign alipay request", ex);
    }
  }

  private String canonical(Map<String, String> values) {
    return values.entrySet().stream().filter(entry -> !"sign".equals(entry.getKey()))
        .sorted(Map.Entry.comparingByKey()).map(entry -> entry.getKey() + "=" + entry.getValue())
        .collect(java.util.stream.Collectors.joining("&"));
  }

  private String form(Map<String, String> values) {
    return values.entrySet().stream().map(entry -> entry.getKey() + "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
        .collect(java.util.stream.Collectors.joining("&"));
  }

  private PrivateKey privateKey() throws Exception { return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(keyBytes(privateKey))); }
  private PublicKey publicKey() throws Exception { return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(keyBytes(publicKey))); }
  private byte[] keyBytes(String value) { return Base64.getDecoder().decode(value.replaceAll("-----[^-]+-----|\\s", "")); }
  private String amount(int amountCent) { return String.format(java.util.Locale.ROOT, "%.2f", amountCent / 100.0); }
}
