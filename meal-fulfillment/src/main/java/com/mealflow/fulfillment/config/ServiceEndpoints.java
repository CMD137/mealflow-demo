package com.mealflow.fulfillment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mealflow.services")
public record ServiceEndpoints(String order, String queue) {
}
