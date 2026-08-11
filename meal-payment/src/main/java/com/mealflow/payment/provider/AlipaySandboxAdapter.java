package com.mealflow.payment.provider;

import java.net.URLEncoder;
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

  public AlipaySandboxAdapter(@Value("${mealflow.payment.alipay.app-id:}") String appId,
      @Value("${mealflow.payment.alipay.private-key:}") String privateKey,
      @Value("${mealflow.payment.alipay.public-key:}") String publicKey,
      @Value("${mealflow.payment.alipay.notify-url:}") String notifyUrl) {
    this.appId = appId;
    this.privateKey = privateKey;
    this.publicKey = publicKey;
    this.notifyUrl = notifyUrl;
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
