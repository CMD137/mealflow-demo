package com.mealflow.authuser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mealflow.authuser.api.AddressRequest;
import com.mealflow.authuser.api.AddressView;
import com.mealflow.authuser.api.EmployeeRequest;
import com.mealflow.authuser.api.EmployeeView;
import com.mealflow.authuser.api.LoginRequest;
import com.mealflow.authuser.api.LoginResponse;
import com.mealflow.authuser.api.SignInView;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {"spring.cloud.nacos.discovery.enabled=false", "mealflow.auth.otp.mode=memory",
        "mealflow.auth.otp.test-code=123456"}
)
class AuthUserPersistenceTest {
  @Autowired
  private AuthUserService authUserService;

  @Test
  void logsInExistingAndCreatesNewUserInDatabase() {
    authUserService.requestLoginCode("13800000000");
    LoginResponse existing = authUserService.login(new LoginRequest("13800000000", "123456"));
    authUserService.requestLoginCode("13900000000");
    LoginResponse created = authUserService.login(new LoginRequest("13900000000", "123456"));

    assertThat(existing.userId()).isEqualTo(100L);
    assertThat(existing.token()).startsWith("mf_");
    assertThat(existing.roleCode()).isEqualTo("MERCHANT_ADMIN");
    assertThat(existing.merchantId()).isEqualTo(10L);
    assertThat(existing.permissions()).contains("MERCHANT_MANAGE", "CATALOG_MANAGE").doesNotContain("INTERNAL_OPERATE");
    assertThat(existing.menus()).extracting("menuCode").contains("catalog").doesNotContain("operations");
    assertThat(created.userId()).isGreaterThan(1000L);
    assertThat(created.roleCode()).isEqualTo("CUSTOMER");
    assertThat(authUserService.validateToken(created.token()).userId()).isEqualTo(created.userId());
    assertThat(authUserService.get(created.userId()).phone()).isEqualTo("13900000000");
    assertThat(authUserService.addresses(100L)).isNotEmpty();
  }

  @Test
  void managesUserAddressesInDatabase() {
    AddressView created = authUserService.addAddress(100L,
        new AddressRequest("Alice", "13800009999", "Building 1"));

    assertThat(created.addressId()).isGreaterThan(1000L);
    assertThat(created.defaultAddress()).isFalse();
    assertThat(authUserService.addresses(100L))
        .anySatisfy(address -> {
          assertThat(address.addressId()).isEqualTo(created.addressId());
          assertThat(address.contactName()).isEqualTo("Alice");
        });

    AddressView updated = authUserService.updateAddress(100L, created.addressId(),
        new AddressRequest("Bob", "13800008888", "Building 2"));

    assertThat(updated.contactName()).isEqualTo("Bob");
    assertThat(updated.phone()).isEqualTo("13800008888");
    assertThat(updated.detail()).isEqualTo("Building 2");

    AddressView defaultAddress = authUserService.setDefaultAddress(100L, created.addressId());
    assertThat(defaultAddress.defaultAddress()).isTrue();
    assertThat(authUserService.addresses(100L))
        .filteredOn(AddressView::defaultAddress)
        .singleElement()
        .extracting(AddressView::addressId)
        .isEqualTo(created.addressId());

    authUserService.deleteAddress(100L, created.addressId());

    assertThat(authUserService.addresses(100L))
        .noneSatisfy(address -> assertThat(address.addressId()).isEqualTo(created.addressId()));
  }

  @Test
  void usesReadOnlyRolesAndSingleMerchantEmployees() {
    assertThat(authUserService.roles()).extracting("roleCode").contains("STORE_STAFF");
    assertThat(authUserService.menus()).extracting("menuCode").contains("catalog", "fulfillment");

    EmployeeView employee = authUserService.addEmployee(10L,
        new EmployeeRequest("13800000066", "Kitchen Lead", "STORE_STAFF"));

    assertThat(employee.employeeId()).isGreaterThan(1000L);
    assertThat(employee.merchantId()).isEqualTo(10L);
    assertThat(employee.roleCode()).isEqualTo("STORE_STAFF");
    assertThat(authUserService.employees(10L, 1, 100).items()).extracting("phone").contains("13800000066");

    authUserService.requestLoginCode("13800000066");
    LoginResponse login = authUserService.login(new LoginRequest("13800000066", "123456"));
    assertThat(login.permissions()).contains("CATALOG_MANAGE", "FULFILLMENT_OPERATE");
    assertThat(login.menus()).extracting("menuCode").contains("catalog", "fulfillment");

    assertThatThrownBy(() -> authUserService.addEmployee(11L,
        new EmployeeRequest("13800000066", "Kitchen Lead", "STORE_STAFF")))
        .hasMessageContaining("only one merchant");

    EmployeeView disabled = authUserService.changeEmployeeStatus(10L, employee.employeeId(), "DISABLED");
    assertThat(disabled.status()).isEqualTo("DISABLED");
    assertThat(authUserService.validateToken(login.token())).isNull();
  }

  @Test
  void signInPersistsPointsLedgerAndRejectsDuplicate() {
    SignInView first = authUserService.signIn(100L);

    assertThat(first.signedToday()).isTrue();
    // First sign-in of a streak: 5 base + 1 streak bonus = 6.
    assertThat(first.todayRewardPoints()).isEqualTo(6);
    assertThat(first.totalPoints()).isEqualTo(6);
    assertThat(first.monthSignDates()).contains(LocalDate.now().toString());

    // Same-day duplicate must be a read-only no-op: no second ledger row, no double reward.
    SignInView second = authUserService.signIn(100L);
    assertThat(second.todayRewardPoints()).isZero();
    assertThat(second.totalPoints()).isEqualTo(6);
    assertThat(second.monthSignDates()).containsExactly(LocalDate.now().toString());

    // Reads (sign info) come from the MySQL fact, not Redis.
    SignInView info = authUserService.signInfo(100L);
    assertThat(info.totalPoints()).isEqualTo(6);
    assertThat(info.continuousDays()).isEqualTo(1);
  }

  @Test
  void logsInPlatformAdministratorWithoutMerchantOwnership() {
    authUserService.requestLoginCode("13800000006");

    LoginResponse platformAdmin = authUserService.login(new LoginRequest("13800000006", "123456"));

    assertThat(platformAdmin.roleCode()).isEqualTo("PLATFORM_ADMIN");
    assertThat(platformAdmin.merchantId()).isNull();
    assertThat(platformAdmin.permissions()).contains("PLATFORM_VOUCHER_MANAGE").doesNotContain("MERCHANT_MANAGE");
    assertThat(authUserService.validateToken(platformAdmin.token()).merchantId()).isNull();
  }

  @Test
  void keepsSignInStateIsolatedByUser() {
    SignInView userA = authUserService.signIn(101L);
    SignInView userB = authUserService.signInfo(102L);

    assertThat(userA.signedToday()).isTrue();
    assertThat(userB.signedToday()).isFalse();
    assertThat(userB.monthSignDates()).isEmpty();
    assertThat(userB.totalPoints()).isZero();
  }
}
