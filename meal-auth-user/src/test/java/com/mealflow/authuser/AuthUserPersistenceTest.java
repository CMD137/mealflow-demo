package com.mealflow.authuser;

import static org.assertj.core.api.Assertions.assertThat;

import com.mealflow.authuser.api.AddressRequest;
import com.mealflow.authuser.api.AddressView;
import com.mealflow.authuser.api.EmployeeRequest;
import com.mealflow.authuser.api.EmployeeView;
import com.mealflow.authuser.api.LoginRequest;
import com.mealflow.authuser.api.LoginResponse;
import com.mealflow.authuser.api.RoleRequest;
import com.mealflow.authuser.api.RoleView;
import java.util.List;
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
    assertThat(existing.permissions()).contains("MERCHANT_MANAGE", "CATALOG_MANAGE", "INTERNAL_OPERATE");
    assertThat(existing.menus()).extracting("menuCode").contains("catalog", "operations");
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

    authUserService.deleteAddress(100L, created.addressId());

    assertThat(authUserService.addresses(100L))
        .noneSatisfy(address -> assertThat(address.addressId()).isEqualTo(created.addressId()));
  }

  @Test
  void managesMerchantRolesMenusAndEmployeesInDatabase() {
    RoleView role = authUserService.saveRole(new RoleRequest("KITCHEN_MANAGER", "Kitchen Manager",
        "Can maintain catalog and operate kitchen", List.of("CATALOG_MANAGE", "FULFILLMENT_OPERATE")));

    assertThat(role.permissions()).containsExactlyInAnyOrder("CATALOG_MANAGE", "FULFILLMENT_OPERATE");
    assertThat(authUserService.roles()).extracting("roleCode").contains("KITCHEN_MANAGER");
    assertThat(authUserService.menus()).extracting("menuCode").contains("catalog", "fulfillment");

    EmployeeView employee = authUserService.addEmployee(10L,
        new EmployeeRequest("13800000066", "Kitchen Lead", "KITCHEN_MANAGER"));

    assertThat(employee.employeeId()).isGreaterThan(1000L);
    assertThat(employee.merchantId()).isEqualTo(10L);
    assertThat(employee.roleCode()).isEqualTo("KITCHEN_MANAGER");
    assertThat(authUserService.employees(10L)).extracting("phone").contains("13800000066");

    LoginResponse login = authUserService.login(new LoginRequest("13800000066", "123456"));
    assertThat(login.permissions()).contains("CATALOG_MANAGE", "FULFILLMENT_OPERATE");
    assertThat(login.menus()).extracting("menuCode").contains("catalog", "fulfillment");

    EmployeeView disabled = authUserService.changeEmployeeStatus(10L, employee.employeeId(), "DISABLED");
    assertThat(disabled.status()).isEqualTo("DISABLED");
    assertThat(authUserService.validateToken(login.token())).isNull();
  }
}
