package com.mealflow.common.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class InternalSignatureTest {

  private static final String SECRET = "test-secret";
  private static final String SERVICE = "meal-order";

  @Test
  void signAndVerifyRoundTrip() {
    String timestamp = Long.toString(System.currentTimeMillis());
    String nonce = "nonce-1";
    String canonical = InternalSignature.canonical(SERVICE, "POST", "/catalog/internal/stocks/reserve", null,
        timestamp, nonce);
    String signature = InternalSignature.sign(SECRET, canonical);

    assertThat(InternalSignature.verify(SECRET, SERVICE, "POST", "/catalog/internal/stocks/reserve", null,
        timestamp, nonce, signature)).isTrue();
  }

  @Test
  void wrongSecretFails() {
    String timestamp = Long.toString(System.currentTimeMillis());
    String nonce = "nonce-2";
    String canonical = InternalSignature.canonical(SERVICE, "GET", "/queue/tickets/1", "", timestamp, nonce);
    String signature = InternalSignature.sign(SECRET, canonical);

    assertThat(InternalSignature.verify("other-secret", SERVICE, "GET", "/queue/tickets/1", "", timestamp, nonce,
        signature)).isFalse();
  }

  @Test
  void tamperedPathFails() {
    String timestamp = Long.toString(System.currentTimeMillis());
    String nonce = "nonce-3";
    String canonical = InternalSignature.canonical(SERVICE, "POST", "/orders/1/pay-success", null, timestamp, nonce);
    String signature = InternalSignature.sign(SECRET, canonical);

    assertThat(InternalSignature.verify(SECRET, SERVICE, "POST", "/orders/2/pay-success", null, timestamp, nonce,
        signature)).isFalse();
  }

  @Test
  void nonceCacheRejectsReplay() {
    NonceCache cache = new NonceCache(16, 300_000L);
    long now = System.currentTimeMillis();
    assertThat(cache.accept(SERVICE, "n1", now)).isTrue();
    assertThat(cache.accept(SERVICE, "n1", now)).isFalse();
    assertThat(cache.accept(SERVICE, "n2", now)).isTrue();
  }
}
