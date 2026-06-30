package com.mealflow.catalog;

import com.mealflow.catalog.api.CategoryRequest;
import com.mealflow.catalog.api.CategoryView;
import com.mealflow.catalog.api.ImageUploadView;
import com.mealflow.catalog.api.OrderItemSnapshot;
import com.mealflow.catalog.api.OrderSkuItem;
import com.mealflow.catalog.api.ReserveStockRequest;
import com.mealflow.catalog.api.ReserveStockResponse;
import com.mealflow.catalog.api.SkuAdminRequest;
import com.mealflow.catalog.api.SkuStatusRequest;
import com.mealflow.catalog.api.SkuStockRequest;
import com.mealflow.catalog.api.SkuView;
import com.mealflow.catalog.api.StockReservationView;
import com.mealflow.catalog.api.StockTransitionRequest;
import com.mealflow.catalog.storage.CatalogImageService;
import com.mealflow.common.api.Result;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/catalog")
public class CatalogController {
  private final CatalogService catalogService;
  private final CatalogImageService catalogImageService;
  private final long defaultMerchantId;

  public CatalogController(CatalogService catalogService,
      CatalogImageService catalogImageService,
      @Value("${mealflow.demo.default-merchant-id:10}") long defaultMerchantId) {
    this.catalogService = catalogService;
    this.catalogImageService = catalogImageService;
    this.defaultMerchantId = defaultMerchantId;
  }

  @GetMapping("/merchants/{merchantId}/skus")
  public Result<List<SkuView>> listSkus(@PathVariable long merchantId) {
    return Result.ok(catalogService.listByMerchant(merchantId));
  }

  @GetMapping("/merchants/{merchantId}/categories")
  public Result<List<CategoryView>> listCategories(@PathVariable long merchantId) {
    return Result.ok(catalogService.listCategories(merchantId));
  }

  @GetMapping("/images/{objectKey}")
  public ResponseEntity<Resource> image(@PathVariable String objectKey) {
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(catalogImageService.contentType(objectKey)))
        .body(catalogImageService.load(objectKey));
  }

  @GetMapping("/admin/categories")
  public Result<List<CategoryView>> adminCategories(
      @RequestHeader(value = "X-Merchant-Id", required = false) Long merchantId) {
    return Result.ok(catalogService.adminCategories(merchantId == null ? defaultMerchantId : merchantId));
  }

  @PostMapping("/admin/categories")
  public Result<CategoryView> createCategory(
      @RequestHeader(value = "X-Merchant-Id", required = false) Long merchantId,
      @Valid @RequestBody CategoryRequest request) {
    return Result.ok(catalogService.createCategory(merchantId == null ? defaultMerchantId : merchantId, request));
  }

  @PutMapping("/admin/categories/{categoryId}")
  public Result<CategoryView> updateCategory(
      @RequestHeader(value = "X-Merchant-Id", required = false) Long merchantId,
      @PathVariable long categoryId,
      @Valid @RequestBody CategoryRequest request) {
    return Result.ok(catalogService.updateCategory(merchantId == null ? defaultMerchantId : merchantId, categoryId,
        request));
  }

  @GetMapping("/admin/skus")
  public Result<List<SkuView>> adminSkus(
      @RequestHeader(value = "X-Merchant-Id", required = false) Long merchantId) {
    return Result.ok(catalogService.adminSkus(merchantId == null ? defaultMerchantId : merchantId));
  }

  @PostMapping(value = "/admin/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public Result<ImageUploadView> uploadImage(
      @RequestHeader(value = "X-Merchant-Id", required = false) Long merchantId,
      @RequestParam("file") MultipartFile file) {
    return Result.ok(catalogImageService.upload(merchantId == null ? defaultMerchantId : merchantId, file));
  }

  @PostMapping("/admin/skus")
  public Result<SkuView> createSku(
      @RequestHeader(value = "X-Merchant-Id", required = false) Long merchantId,
      @Valid @RequestBody SkuAdminRequest request) {
    return Result.ok(catalogService.createSku(merchantId == null ? defaultMerchantId : merchantId, request));
  }

  @PutMapping("/admin/skus/{skuId}")
  public Result<SkuView> updateSku(
      @RequestHeader(value = "X-Merchant-Id", required = false) Long merchantId,
      @PathVariable long skuId,
      @Valid @RequestBody SkuAdminRequest request) {
    return Result.ok(catalogService.updateSku(merchantId == null ? defaultMerchantId : merchantId, skuId, request));
  }

  @PutMapping("/admin/skus/{skuId}/status")
  public Result<SkuView> updateSkuStatus(
      @RequestHeader(value = "X-Merchant-Id", required = false) Long merchantId,
      @PathVariable long skuId,
      @Valid @RequestBody SkuStatusRequest request) {
    return Result.ok(catalogService.updateSkuStatus(merchantId == null ? defaultMerchantId : merchantId, skuId,
        request.status()));
  }

  @PutMapping("/admin/skus/{skuId}/stock")
  public Result<SkuView> updateSkuStock(
      @RequestHeader(value = "X-Merchant-Id", required = false) Long merchantId,
      @PathVariable long skuId,
      @Valid @RequestBody SkuStockRequest request) {
    return Result.ok(catalogService.updateSkuStock(merchantId == null ? defaultMerchantId : merchantId, skuId,
        request.stock()));
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
