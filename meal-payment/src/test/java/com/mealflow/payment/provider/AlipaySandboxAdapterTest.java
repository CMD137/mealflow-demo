package com.mealflow.payment.provider;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradeRefundRequest;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class AlipaySandboxAdapterTest {
  private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  @Test
  void checkoutUsesOfficialSdkAndExpectedAlipayParameters() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(1024);
    String privateKey = Base64.getEncoder().encodeToString(generator.generateKeyPair().getPrivate().getEncoded());
    AlipaySandboxAdapter adapter = new AlipaySandboxAdapter(
        "test-app", privateKey, "", "https://example.test/callback");

    LocalDateTime before = LocalDateTime.now().minusSeconds(2);
    String checkoutUrl = adapter.checkoutUrl(10001L, 1);
    LocalDateTime after = LocalDateTime.now().plusSeconds(2);
    Map<String, String> query = Stream.of(URI.create(checkoutUrl).getRawQuery().split("&"))
        .map(value -> value.split("=", 2))
        .collect(Collectors.toMap(value -> value[0],
            value -> URLDecoder.decode(value[1], StandardCharsets.UTF_8)));
    LocalDateTime timestamp = LocalDateTime.parse(query.get("timestamp"), FORMAT);

    assertEquals("alipay.trade.page.pay", query.get("method"));
    assertEquals("RSA2", query.get("sign_type"));
    assertEquals("https://example.test/callback", query.get("notify_url"));
    assertTrue(query.get("alipay_sdk").startsWith("alipay-sdk-java-"));
    assertTrue(query.get("biz_content").contains("\"total_amount\":\"0.01\""));
    assertFalse(timestamp.isBefore(before));
    assertTrue(timestamp.isBefore(after));
  }

  @Test
  void syncResponseVerificationIsCryptographicallySound() throws Exception {
    // The verification logic itself must work when the key matches; the sandbox failure is a
    // platform-side key mismatch, not a code defect.
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(1024);
    java.security.KeyPair pair = generator.generateKeyPair();
    String privateKey = Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded());
    String publicKey = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
    AlipaySandboxAdapter adapter = new AlipaySandboxAdapter("test-app", privateKey, publicKey, "");

    String responseJson = "{\"code\":\"10000\",\"msg\":\"Success\",\"trade_no\":\"T123\","
        + "\"out_trade_no\":\"MF1\",\"fund_change\":\"Y\",\"refund_fee\":\"0.01\"}";
    String sign = AlipaySignature.rsaSign(responseJson, privateKey, "UTF-8", "RSA2");
    String body = "{\"alipay_trade_refund_response\":" + responseJson
        + ",\"sign\":\"" + sign + "\",\"sign_type\":\"RSA2\"}";

    assertTrue(adapter.verifySyncSignature(new AlipayTradeRefundRequest(), body),
        "signature must verify when public key matches the signing key");

    String tampered = body.replace("\"msg\":\"Success\"", "\"msg\":\"Tampered\"");
    assertFalse(adapter.verifySyncSignature(new AlipayTradeRefundRequest(), tampered),
        "tampered response must not verify");
  }

}
