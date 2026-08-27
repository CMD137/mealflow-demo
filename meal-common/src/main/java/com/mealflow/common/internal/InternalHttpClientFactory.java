package com.mealflow.common.internal;

import java.time.Duration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

/**
 * Builds internal HTTP clients with sane timeouts and automatic request signing.
 *
 * <p>Every business service used to create a bare {@code new RestTemplate()} with default
 * (effectively infinite) timeouts; a slow downstream could pin threads and database connections
 * forever. All internal clients now go through this factory: connect 500ms / read 2s by default,
 * plus the {@link InternalSigningInterceptor} when the internal secret is configured.</p>
 */
public final class InternalHttpClientFactory {

  private InternalHttpClientFactory() {
  }

  public static RestTemplate restTemplate(InternalAuthProperties properties, Duration connectTimeout,
      Duration readTimeout) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Math.toIntExact(connectTimeout.toMillis()));
    factory.setReadTimeout(Math.toIntExact(readTimeout.toMillis()));
    RestTemplate restTemplate = new RestTemplate(factory);
    if (properties.isConfigured()) {
      restTemplate.getInterceptors().add(new InternalSigningInterceptor(new InternalRequestSigner(properties)));
    }
    return restTemplate;
  }

  public static RestClient.Builder restClientBuilder(InternalAuthProperties properties, Duration connectTimeout,
      Duration readTimeout) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Math.toIntExact(connectTimeout.toMillis()));
    factory.setReadTimeout(Math.toIntExact(readTimeout.toMillis()));
    RestClient.Builder builder = RestClient.builder().requestFactory(factory);
    if (properties.isConfigured()) {
      builder = builder.requestInterceptor(new InternalSigningInterceptor(new InternalRequestSigner(properties)));
    }
    return builder;
  }
}
