package com.mealflow.app.promotion;

import com.mealflow.common.api.Result;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/vouchers")
public class PromotionController {
  private final PromotionService promotionService;
  private final long defaultUserId;

  public PromotionController(PromotionService promotionService,
      @Value("${mealflow.demo.default-user-id}") long defaultUserId) {
    this.promotionService = promotionService;
    this.defaultUserId = defaultUserId;
  }

  @PostMapping("/{voucherId}/seckill")
  public Result<SeckillVoucherResponse> seckill(@PathVariable long voucherId,
      @RequestHeader(value = "X-User-Id", required = false) Long userId,
      @Valid @RequestBody SeckillVoucherRequest request) {
    return Result.ok(promotionService.seckill(userId == null ? defaultUserId : userId, voucherId, request.requestId()));
  }

  @GetMapping("/wallet")
  public Result<List<UserVoucherView>> wallet(@RequestHeader(value = "X-User-Id", required = false) Long userId) {
    return Result.ok(promotionService.wallet(userId == null ? defaultUserId : userId));
  }
}
