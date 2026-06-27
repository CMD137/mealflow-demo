package com.mealflow.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.mealflow.catalog.api.OrderSkuItem;
import com.mealflow.catalog.api.ReserveStockRequest;
import com.mealflow.catalog.api.ReserveStockResponse;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = "spring.cloud.nacos.discovery.enabled=false"
)
class CatalogPersistenceTest {
  @Autowired
  private CatalogService catalogService;

  @Test
  void reservesAndConfirmsStockInDatabase() {
    ReserveStockRequest request = new ReserveStockRequest("catalog-test-1", 1L, 10L, null, 1001L,
        List.of(new OrderSkuItem(1L, 1)), LocalDateTime.now().plusMinutes(10));

    ReserveStockResponse response = catalogService.reserve(request);

    assertThat(response.status()).isEqualTo("RESERVED");
    assertThat(response.reservationIds()).hasSize(1);

    catalogService.confirm(response.reservationIds(), 1001L);

    assertThat(catalogService.reservations())
        .anySatisfy(reservation -> {
          assertThat(reservation.reservationId()).isEqualTo(response.reservationIds().get(0));
          assertThat(reservation.status()).isEqualTo("CONFIRMED");
        });
  }
}
