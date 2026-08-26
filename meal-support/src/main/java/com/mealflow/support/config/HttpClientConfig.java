package com.mealflow.support.config;

import com.mealflow.common.internal.InternalAuthProperties;
import com.mealflow.common.internal.InternalHttpClientFactory;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Shared internal HTTP client for the support bridge: timeouts plus HMAC signing so the
 * {@code X-User-Id} header forwarded to peer services is backed by a verifiable service identity.
 */
@Configuration
public class HttpClientConfig {

  private final InternalAuthProperties internalAuthProperties;

  public HttpClientConfig(InternalAuthProperties internalAuthProperties) {
    this.internalAuthProperties = internalAuthProperties;
  }

  @Bean
  RestClient restClient() {
    return InternalHttpClientFactory.restClientBuilder(internalAuthProperties,
        Duration.ofMillis(500), Duration.ofSeconds(2)).build();
  }
}
