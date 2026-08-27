package com.mealflow.fulfillment.config;

import com.mealflow.common.internal.InternalAuthProperties;
import com.mealflow.common.internal.InternalHttpClientFactory;
import java.time.Duration;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Shared internal HTTP client with timeouts, HMAC signing and Nacos-based service-name
 * resolution for order/queue calls.
 */
@Configuration
public class HttpClientConfig {

  private final InternalAuthProperties internalAuthProperties;

  public HttpClientConfig(InternalAuthProperties internalAuthProperties) {
    this.internalAuthProperties = internalAuthProperties;
  }

  @Bean
  @LoadBalanced
  RestTemplate restTemplate() {
    return InternalHttpClientFactory.restTemplate(internalAuthProperties,
        Duration.ofMillis(500), Duration.ofSeconds(2));
  }
}
