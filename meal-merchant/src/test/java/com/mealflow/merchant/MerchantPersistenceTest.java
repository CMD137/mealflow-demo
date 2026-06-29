package com.mealflow.merchant;

import static org.assertj.core.api.Assertions.assertThat;

import com.mealflow.merchant.api.BusinessStatusRequest;
import com.mealflow.merchant.api.CapacityConfigRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = "spring.cloud.nacos.discovery.enabled=false"
)
class MerchantPersistenceTest {
  @Autowired
  private MerchantService merchantService;

  @Test
  void updatesMerchantCapacityInDatabase() {
    assertThat(merchantService.list()).isNotEmpty();

    merchantService.updateCapacity(10L, new CapacityConfigRequest(4, 0.8));

    assertThat(merchantService.get(10L).baseCapacity()).isEqualTo(4);
    assertThat(merchantService.get(10L).manualFactor()).isEqualTo(0.8);
  }

  @Test
  void updatesMerchantBusinessStatusInDatabase() {
    merchantService.updateBusinessStatus(10L, new BusinessStatusRequest("closed"));

    assertThat(merchantService.get(10L).businessStatus()).isEqualTo("CLOSED");

    merchantService.updateBusinessStatus(10L, new BusinessStatusRequest("OPEN"));

    assertThat(merchantService.get(10L).businessStatus()).isEqualTo("OPEN");
  }
}
