package com.mealflow.common.internal;

import jakarta.servlet.Filter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Registers {@link InternalAuthFilter} for Spring MVC services. Every non-public request must
 * carry a valid HMAC signature; the reactive gateway is the trust anchor and validates nothing.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(Filter.class)
public class InternalAuthMvcAutoConfiguration {

  @Bean
  public FilterRegistrationBean<InternalAuthFilter> internalAuthFilter(InternalAuthProperties properties) {
    FilterRegistrationBean<InternalAuthFilter> registration =
        new FilterRegistrationBean<>(new InternalAuthFilter(properties));
    registration.addUrlPatterns("/*");
    registration.setOrder(-900);
    return registration;
  }
}
