package com.mealflow.app.catalog;

import com.mealflow.common.api.Result;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
}
