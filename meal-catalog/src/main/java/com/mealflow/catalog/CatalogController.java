package com.mealflow.catalog;

import com.mealflow.catalog.api.OrderItemSnapshot;
import com.mealflow.catalog.api.OrderSkuItem;
import com.mealflow.catalog.api.ReserveStockRequest;
import com.mealflow.catalog.api.ReserveStockResponse;
import com.mealflow.catalog.api.SkuView;
import com.mealflow.catalog.api.StockReservationView;
import com.mealflow.catalog.api.StockTransitionRequest;
import com.mealflow.common.api.Result;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/catalog")
public class CatalogController {
  private final CatalogService catalogService;

  public CatalogController(CatalogService catalogService) {
    this.catalogService = catalogService;
  }

  @GetMapping("/merchants/{merchantId}/skus")
  public Result<List<SkuView>> listSkus(@PathVariable long merchantId) {
    return Result.ok(catalogService.listByMerchant(merchantId));
  }

  @PostMapping("/internal/stocks/snapshots/{merchantId}")
  public Result<List<OrderItemSnapshot>> snapshots(@PathVariable long merchantId,
      @Valid @RequestBody List<OrderSkuItem> items) {
    return Result.ok(catalogService.buildSnapshots(merchantId, items));
  }

  @PostMapping("/internal/stocks/reserve")
  public Result<ReserveStockResponse> reserve(@Valid @RequestBody ReserveStockRequest request) {
    return Result.ok(catalogService.reserve(request));
  }

  @PostMapping("/internal/stocks/confirm")
  public Result<Void> confirm(@Valid @RequestBody StockTransitionRequest request) {
    catalogService.confirm(request.reservationIds(), request.orderId());
    return Result.ok();
  }

  @PostMapping("/internal/stocks/release")
  public Result<Void> release(@Valid @RequestBody StockTransitionRequest request) {
    catalogService.release(request.reservationIds());
    return Result.ok();
  }

  @GetMapping("/internal/stocks/reservations")
  public Result<List<StockReservationView>> reservations() {
    return Result.ok(catalogService.reservations());
  }
}
