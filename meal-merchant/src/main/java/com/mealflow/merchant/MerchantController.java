package com.mealflow.merchant;

import com.mealflow.common.api.Result;
import com.mealflow.common.api.PageResult;
import com.mealflow.common.security.RequestIdentity;
import com.mealflow.merchant.api.CapacityConfigRequest;
import com.mealflow.merchant.api.BusinessStatusRequest;
import com.mealflow.merchant.api.MerchantView;
import com.mealflow.merchant.api.SystemMerchantStatusRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/merchants")
public class MerchantController {
  private final MerchantService merchantService;

  public MerchantController(MerchantService merchantService) {
    this.merchantService = merchantService;
  }

  @GetMapping
  public Result<List<MerchantView>> list() {
    return Result.ok(merchantService.list());
  }

  @GetMapping("/{merchantId}")
  public Result<MerchantView> get(@PathVariable long merchantId) {
    return Result.ok(merchantService.get(merchantId));
  }

  @GetMapping("/system")
  public Result<PageResult<MerchantView>> systemMerchants(
      @RequestHeader(value = "X-Role", required = false) String roleCode,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int pageSize,
      @RequestParam(required = false) String name,
      @RequestParam(required = false) String status) {
    RequestIdentity.requireRole("SYSTEM_ADMIN", roleCode);
    return Result.ok(merchantService.systemMerchants(page, pageSize, name, status));
  }

  @PutMapping("/system/{merchantId}/business-status")
  public Result<MerchantView> updateSystemBusinessStatus(@PathVariable long merchantId,
      @RequestHeader(value = "X-Role", required = false) String roleCode,
      @Valid @RequestBody SystemMerchantStatusRequest request) {
    RequestIdentity.requireRole("SYSTEM_ADMIN", roleCode);
    return Result.ok(merchantService.updateSystemBusinessStatus(merchantId, request));
  }

  @PostMapping("/{merchantId}/capacity")
  public Result<MerchantView> updateCapacity(@PathVariable long merchantId,
      @RequestHeader(value = "X-Merchant-Id", required = false) Long currentMerchantId,
      @Valid @RequestBody CapacityConfigRequest request) {
    RequestIdentity.requireMerchant(merchantId, currentMerchantId);
    return Result.ok(merchantService.updateCapacity(merchantId, request));
  }

  @PostMapping("/{merchantId}/business-status")
  public Result<MerchantView> updateBusinessStatus(@PathVariable long merchantId,
      @RequestHeader(value = "X-Merchant-Id", required = false) Long currentMerchantId,
      @Valid @RequestBody BusinessStatusRequest request) {
    RequestIdentity.requireMerchant(merchantId, currentMerchantId);
    return Result.ok(merchantService.updateBusinessStatus(merchantId, request));
  }
}
