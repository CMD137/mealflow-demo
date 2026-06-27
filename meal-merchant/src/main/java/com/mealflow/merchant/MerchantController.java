package com.mealflow.merchant;

import com.mealflow.common.api.Result;
import com.mealflow.merchant.api.CapacityConfigRequest;
import com.mealflow.merchant.api.MerchantView;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

  @PostMapping("/{merchantId}/capacity")
  public Result<MerchantView> updateCapacity(@PathVariable long merchantId,
      @Valid @RequestBody CapacityConfigRequest request) {
    return Result.ok(merchantService.updateCapacity(merchantId, request));
  }
}
