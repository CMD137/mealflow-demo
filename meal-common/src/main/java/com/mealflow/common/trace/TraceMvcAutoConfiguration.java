package com.mealflow.common.trace;

import jakarta.servlet.Filter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;

/**
 * Registers {@link TraceFilter} for Spring MVC services. The filter runs before the internal auth
 * filter so that rejected requests still carry a trace id.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(Filter.class)
public class TraceMvcAutoConfiguration {

  @Bean
  public FilterRegistrationBean<TraceFilter> traceFilter() {
    FilterRegistrationBean<TraceFilter> registration = new FilterRegistrationBean<>(new TraceFilter());
    registration.addUrlPatterns("/*");
    registration.setOrder(-1000);
    return registration;
  }
}
