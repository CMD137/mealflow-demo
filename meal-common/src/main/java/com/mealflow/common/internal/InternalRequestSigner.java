package com.mealflow.common.internal;

import java.util.UUID;
import org.springframework.http.HttpHeaders;

/**
 * Adds the {@code X-Internal-*} signature headers to an outgoing request using this service's
 * configured identity. Used by the gateway (WebFlux forward + token-validation WebClient), by
 * {@code RestTemplate}/{@code RestClient} based internal clients, and by the Python agent runtime.
 */
public class InternalRequestSigner {

  private final InternalAuthProperties properties;

  public InternalRequestSigner(InternalAuthProperties properties) {
    this.properties = properties;
  }

  public boolean isConfigured() {
    return properties.isConfigured();
  }

  public String serviceName() {
    return properties.getServiceName();
  }

  /** Adds signature headers to {@code headers} for the given method/path/query. */
  public void sign(HttpHeaders headers, String method, String rawPath, String rawQuery) {
    if (!isConfigured()) {
      return;
    }
    String timestamp = Long.toString(System.currentTimeMillis());
    String nonce = UUID.randomUUID().toString();
    String canonical = InternalSignature.canonical(properties.getServiceName(), method, rawPath, rawQuery,
        timestamp, nonce);
    headers.set(InternalSignature.HEADER_SERVICE, properties.getServiceName());
    headers.set(InternalSignature.HEADER_TIMESTAMP, timestamp);
    headers.set(InternalSignature.HEADER_NONCE, nonce);
    headers.set(InternalSignature.HEADER_SIGNATURE, InternalSignature.sign(properties.getSecret(), canonical));
  }
}
