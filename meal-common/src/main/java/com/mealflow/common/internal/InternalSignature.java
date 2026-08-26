package com.mealflow.common.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * HMAC-SHA256 request signature used for service-to-service authentication.
 *
 * <p>Canonical string signed by the caller:</p>
 *
 * <pre>{@code
 * serviceName
 * METHOD
 * rawPath
 * rawQuery        (empty string when absent)
 * timestampMillis
 * nonce
 * }</pre>
 *
 * <p>The receiver verifies the signature, checks the timestamp is inside the acceptance window and
 * rejects replayed nonces. Body bytes are intentionally not part of the signature in the current
 * version (documented limitation); body integrity is delegated to the transport layer in
 * production. The scheme gives every business service a cheap, symmetric way to prove "this
 * request came from the gateway or a peer service", which is what makes downstream
 * {@code X-User-Id} / {@code X-Merchant-Id} headers trustworthy.</p>
 */
public final class InternalSignature {

  public static final String HEADER_SERVICE = "X-Internal-Service";
  public static final String HEADER_TIMESTAMP = "X-Internal-Timestamp";
  public static final String HEADER_NONCE = "X-Internal-Nonce";
  public static final String HEADER_SIGNATURE = "X-Internal-Signature";

  private InternalSignature() {
  }

  public static String canonical(String service, String method, String rawPath, String rawQuery,
      String timestampMillis, String nonce) {
    String query = rawQuery == null ? "" : rawQuery;
    return service + "\n" + method.toUpperCase() + "\n" + rawPath + "\n" + query + "\n"
        + timestampMillis + "\n" + nonce;
  }

  public static String sign(String secret, String canonical) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception ex) {
      throw new IllegalStateException("failed to sign internal request", ex);
    }
  }

  public static boolean verify(String secret, String service, String method, String rawPath, String rawQuery,
      String timestampMillis, String nonce, String expectedSignature) {
    if (expectedSignature == null || expectedSignature.isBlank()) {
      return false;
    }
    String computed = sign(secret, canonical(service, method, rawPath, rawQuery, timestampMillis, nonce));
    byte[] expected = expectedSignature.trim().toLowerCase().getBytes(StandardCharsets.UTF_8);
    byte[] actual = computed.getBytes(StandardCharsets.UTF_8);
    return MessageDigest.isEqual(actual, expected);
  }
}
