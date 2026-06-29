package com.mealflow.authuser;

import static org.assertj.core.api.Assertions.assertThat;

import com.mealflow.authuser.api.LoginRequest;
import com.mealflow.authuser.api.LoginResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = "spring.cloud.nacos.discovery.enabled=false"
)
class AuthUserPersistenceTest {
  @Autowired
  private AuthUserService authUserService;

  @Test
  void logsInExistingAndCreatesNewUserInDatabase() {
    LoginResponse existing = authUserService.login(new LoginRequest("13800000000", "123456"));
    LoginResponse created = authUserService.login(new LoginRequest("13900000000", "123456"));

    assertThat(existing.userId()).isEqualTo(100L);
    assertThat(existing.token()).startsWith("mf-");
    assertThat(existing.roleCode()).isEqualTo("MERCHANT_ADMIN");
    assertThat(existing.merchantId()).isEqualTo(10L);
    assertThat(existing.permissions()).contains("MERCHANT_MANAGE", "INTERNAL_OPERATE");
    assertThat(created.userId()).isGreaterThan(1000L);
    assertThat(created.roleCode()).isEqualTo("CUSTOMER");
    assertThat(authUserService.validateToken(created.token()).userId()).isEqualTo(created.userId());
    assertThat(authUserService.get(created.userId()).phone()).isEqualTo("13900000000");
    assertThat(authUserService.addresses(100L)).isNotEmpty();
  }
}
