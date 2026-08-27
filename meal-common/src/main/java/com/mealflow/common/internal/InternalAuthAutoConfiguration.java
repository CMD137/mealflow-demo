package com.mealflow.common.internal;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Core internal-auth wiring available to every application (gateway and business services alike):
 * binds {@link InternalAuthProperties} and exposes the {@link InternalRequestSigner} used to sign
 * outbound requests. The reactive gateway only needs this core; Spring MVC services additionally
 * get the {@code InternalAuthMvcAutoConfiguration} validation filter.
 */
@AutoConfiguration
@EnableConfigurationProperties(InternalAuthProperties.class)
public class InternalAuthAutoConfiguration {

  @Bean
  public InternalRequestSigner internalRequestSigner(InternalAuthProperties properties) {
    return new InternalRequestSigner(properties);
  }
}
