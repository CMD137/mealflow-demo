package com.mealflow.order.config;

import com.mealflow.common.internal.InternalAuthProperties;
import com.mealflow.common.internal.InternalHttpClientFactory;
import java.time.Duration;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

/**
 * Shared internal HTTP client: explicit connect/read timeouts, automatic HMAC signing of every
 * call to a peer service, and Spring Cloud LoadBalancer resolution of Nacos-registered service
 * names (e.g. {@code http://meal-catalog}, no hard-coded port).
 */
@Configuration
public class HttpClientConfig {

  private final InternalAuthProperties internalAuthProperties;

  public HttpClientConfig(InternalAuthProperties internalAuthProperties) {
    this.internalAuthProperties = internalAuthProperties;
  }

  @Bean
  @LoadBalanced
  public RestTemplate restTemplate() {
    RestTemplate restTemplate = InternalHttpClientFactory.restTemplate(internalAuthProperties,
        Duration.ofMillis(500), Duration.ofSeconds(5));
    restTemplate.setErrorHandler(new DefaultResponseErrorHandler() {
      @Override
      protected boolean hasError(HttpStatusCode statusCode) {
        return statusCode.is5xxServerError();
      }
    });
    return restTemplate;
  }
}
