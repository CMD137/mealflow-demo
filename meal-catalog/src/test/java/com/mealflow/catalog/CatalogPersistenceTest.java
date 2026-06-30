package com.mealflow.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mealflow.catalog.api.CategoryRequest;
import com.mealflow.catalog.api.CategoryView;
import com.mealflow.catalog.api.OrderSkuItem;
import com.mealflow.catalog.api.ReserveStockRequest;
import com.mealflow.catalog.api.ReserveStockResponse;
import com.mealflow.catalog.api.SkuAdminRequest;
import com.mealflow.catalog.api.SkuView;
import com.mealflow.common.exception.BizException;
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

  @Test
  void managesCategoriesAndSkusForMerchantBackOffice() {
    CategoryView category = catalogService.createCategory(10L,
        new CategoryRequest("Noodles", 30, "ACTIVE"));
    SkuView sku = catalogService.createSku(10L,
        new SkuAdminRequest(category.categoryId(), "Tomato Beef Noodle", "Warm noodle bowl", "", 3200, 12,
            "ON_SHELF"));

    assertThat(category.categoryId()).isGreaterThan(1000L);
    assertThat(sku.skuId()).isGreaterThan(1000L);
    assertThat(sku.categoryName()).isEqualTo("Noodles");
    assertThat(catalogService.adminCategories(10L)).extracting("name").contains("Noodles");
    assertThat(catalogService.listCategories(10L)).extracting("name").contains("Noodles");
    assertThat(catalogService.adminSkus(10L)).extracting("name").contains("Tomato Beef Noodle");
    assertThat(catalogService.listByMerchant(10L)).extracting("skuId").contains(sku.skuId());

    SkuView stocked = catalogService.updateSkuStock(10L, sku.skuId(), 5);
    assertThat(stocked.stock()).isEqualTo(5);

    SkuView offShelf = catalogService.updateSkuStatus(10L, sku.skuId(), "OFF_SHELF");
    assertThat(offShelf.status()).isEqualTo("OFF_SHELF");
    assertThat(catalogService.listByMerchant(10L)).extracting("skuId").doesNotContain(sku.skuId());
    assertThatThrownBy(() -> catalogService.buildSnapshots(10L, List.of(new OrderSkuItem(sku.skuId(), 1))))
        .isInstanceOf(BizException.class)
        .hasMessageContaining("SKU is not on shelf");
  }
}
