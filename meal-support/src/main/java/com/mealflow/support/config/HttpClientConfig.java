package com.mealflow.support.config;

import com.mealflow.common.internal.InternalAuthProperties;
import com.mealflow.common.internal.InternalHttpClientFactory;
import java.time.Duration;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Shared internal HTTP client for the support bridge: timeouts, HMAC signing (so the forwarded
 * {@code X-User-Id} is backed by a verifiable service identity) and Nacos-based service-name
 * resolution for peer services.
 */
@Configuration
public class HttpClientConfig {

  private final InternalAuthProperties internalAuthProperties;

  public HttpClientConfig(InternalAuthProperties internalAuthProperties) {
    this.internalAuthProperties = internalAuthProperties;
  }

  @Bean
  @LoadBalanced
  RestClient.Builder internalRestClientBuilder() {
    return InternalHttpClientFactory.restClientBuilder(internalAuthProperties,
        Duration.ofMillis(500), Duration.ofSeconds(2));
  }

  @Bean
  RestClient restClient(RestClient.Builder internalRestClientBuilder) {
    return internalRestClientBuilder.build();
  }
}
