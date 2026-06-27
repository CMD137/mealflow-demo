package com.mealflow.order.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mealflow.services")
public record ServiceEndpoints(
    String catalog,
    String promotion,
    String queue,
    String payment
) {
}
