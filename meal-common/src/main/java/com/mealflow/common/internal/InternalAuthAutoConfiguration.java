package com.mealflow.common.internal;

import jakarta.servlet.Filter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Wires the internal request authentication for every service:
 *
 * <ul>
 *   <li>{@link InternalRequestSigner} is always available (gateway signs forwards, services sign
 *       outgoing calls);</li>
 *   <li>{@link InternalAuthFilter} validates incoming signatures on non-public paths (Spring MVC
 *       services only; the reactive gateway is the trust anchor and does not validate itself).</li>
 * </ul>
 */
@AutoConfiguration
@ConditionalOnClass(Filter.class)
@EnableConfigurationProperties(InternalAuthProperties.class)
public class InternalAuthAutoConfiguration {

  @Bean
  public InternalRequestSigner internalRequestSigner(InternalAuthProperties properties) {
    return new InternalRequestSigner(properties);
  }

  @Bean
  @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
  @ConditionalOnClass(OncePerRequestFilter.class)
  public FilterRegistrationBean<InternalAuthFilter> internalAuthFilter(InternalAuthProperties properties) {
    FilterRegistrationBean<InternalAuthFilter> registration =
        new FilterRegistrationBean<>(new InternalAuthFilter(properties));
    registration.addUrlPatterns("/*");
    registration.setOrder(-900);
    return registration;
  }
}
