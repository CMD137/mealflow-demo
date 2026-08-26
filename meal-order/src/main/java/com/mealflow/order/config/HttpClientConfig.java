package com.mealflow.order.config;

import com.mealflow.common.internal.InternalAuthProperties;
import com.mealflow.common.internal.InternalHttpClientFactory;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Shared internal HTTP client: explicit connect/read timeouts (a slow downstream can no longer pin
 * order threads and database connections forever) plus automatic HMAC signing of every call to a
 * peer service.
 */
@Configuration
public class HttpClientConfig {

  private final InternalAuthProperties internalAuthProperties;

  public HttpClientConfig(InternalAuthProperties internalAuthProperties) {
    this.internalAuthProperties = internalAuthProperties;
  }

  @Bean
  RestTemplate restTemplate() {
    return InternalHttpClientFactory.restTemplate(internalAuthProperties,
        Duration.ofMillis(500), Duration.ofSeconds(2));
  }
}
