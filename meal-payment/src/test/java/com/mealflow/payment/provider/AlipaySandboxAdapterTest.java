package com.mealflow.payment.provider;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
