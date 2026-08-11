package com.mealflow.authuser.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
public class ProductionSafetyValidator {
  private final String otpMode;
  private final boolean logCode;
  private final String otpPepper;
  private final String sessionPepper;

  public ProductionSafetyValidator(@Value("${mealflow.auth.otp.mode:redis}") String otpMode,
      @Value("${mealflow.auth.otp.log-code:false}") boolean logCode,
      @Value("${mealflow.auth.otp.pepper:}") String otpPepper,
      @Value("${mealflow.auth.session.pepper:}") String sessionPepper) {
    this.otpMode = otpMode;
    this.logCode = logCode;
    this.otpPepper = otpPepper;
    this.sessionPepper = sessionPepper;
  }

  @PostConstruct
  void validate() {
    if (!"redis".equals(otpMode) || logCode || otpPepper.isBlank() || sessionPepper.isBlank()) {
      throw new IllegalStateException("production auth requires Redis OTP without code logging and non-empty peppers");
    }
  }
}
