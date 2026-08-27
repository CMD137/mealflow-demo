package com.mealflow.common.trace;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.web.server.WebFilter;

/**
 * Registers {@link TraceWebFilter} for the WebFlux gateway so the gateway becomes the trace-id
 * origin for every external request.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@ConditionalOnClass(WebFilter.class)
public class TraceReactiveAutoConfiguration {

  @Bean
  @Order(-1000)
  public WebFilter traceWebFilter() {
    return new TraceWebFilter();
  }
}
