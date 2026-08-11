package com.mealflow.payment.config;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
public class ProductionPaymentSafetyValidator implements SmartInitializingSingleton {
  private final String provider;
  private final String appId;
  private final String privateKey;
  private final String publicKey;

  public ProductionPaymentSafetyValidator(@Value("${mealflow.payment.provider:}") String provider,
      @Value("${mealflow.payment.alipay.app-id:}") String appId,
      @Value("${mealflow.payment.alipay.private-key:}") String privateKey,
      @Value("${mealflow.payment.alipay.public-key:}") String publicKey) {
    this.provider = provider;
    this.appId = appId;
    this.privateKey = privateKey;
    this.publicKey = publicKey;
  }

  @Override
  public void afterSingletonsInstantiated() {
    if (!"alipay-sandbox".equals(provider) || appId.isBlank() || privateKey.isBlank() || publicKey.isBlank()) {
      throw new IllegalStateException("production payment requires configured alipay-sandbox credentials");
    }
  }
}
