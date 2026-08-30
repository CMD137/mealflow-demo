package com.mealflow.promotion;

import com.mealflow.common.api.PageResult;
import com.mealflow.common.api.Result;
import com.mealflow.common.security.RequestIdentity;
import com.mealflow.promotion.api.LockVoucherRequest;
import com.mealflow.promotion.api.SeckillVoucherRequest;
import com.mealflow.promotion.api.SeckillVoucherResponse;
import com.mealflow.promotion.api.UserVoucherView;
import com.mealflow.promotion.api.VoucherAdminRequest;
import com.mealflow.promotion.api.VoucherClaimView;
import com.mealflow.promotion.api.VoucherClaimRetryView;
import com.mealflow.promotion.api.VoucherLockResponse;
import com.mealflow.promotion.api.VoucherLockView;
import com.mealflow.promotion.api.VoucherTransitionRequest;
import com.mealflow.promotion.api.VoucherView;
import com.mealflow.promotion.seckill.VoucherClaimPendingRecoveryScheduler;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/vouchers")
public class PromotionController {
  private final PromotionService promotionService;
  private final VoucherClaimPendingRecoveryScheduler pendingRecoveryScheduler;

  public PromotionController(PromotionService promotionService,
      VoucherClaimPendingRecoveryScheduler pendingRecoveryScheduler) {
    this.promotionService = promotionService;
    this.pendingRecoveryScheduler = pendingRecoveryScheduler;
  }

  @GetMapping("/{voucherId}/claims/me")
  public Result<SeckillVoucherResponse> myClaim(@PathVariable long voucherId,
      @RequestHeader(value = "X-User-Id", required = false) Long userId) {
    return Result.ok(promotionService.claimStatus(RequestIdentity.requireUser(userId), voucherId));
  }

  @PostMapping("/{voucherId}/seckill")
  public Result<SeckillVoucherResponse> seckill(@PathVariable long voucherId,
      @RequestHeader(value = "X-User-Id", required = false) Long userId,
      @Valid @RequestBody SeckillVoucherRequest request) {
    return Result.ok(promotionService.seckill(RequestIdentity.requireUser(userId), voucherId, request.requestId()));
  }

  @GetMapping("/wallet")
  public Result<List<UserVoucherView>> wallet(@RequestHeader(value = "X-User-Id", required = false) Long userId) {
    return Result.ok(promotionService.wallet(RequestIdentity.requireUser(userId)));
  }

  @GetMapping
  public Result<List<VoucherView>> availableVouchers() {
    return Result.ok(promotionService.activeVouchers());
  }

  @GetMapping("/admin")
  public Result<PageResult<VoucherView>> vouchers(@RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int pageSize) {
    return Result.ok(promotionService.vouchers(page, pageSize));
  }

  @PostMapping("/admin")
  public Result<VoucherView> createVoucher(@Valid @RequestBody VoucherAdminRequest request) {
    return Result.ok(promotionService.createVoucher(request));
  }

  @PutMapping("/admin/{voucherId}")
  public Result<VoucherView> updateVoucher(@PathVariable long voucherId,
      @Valid @RequestBody VoucherAdminRequest request) {
    return Result.ok(promotionService.updateVoucher(voucherId, request));
  }

  @PostMapping("/internal/lock")
  public Result<VoucherLockResponse> lock(@Valid @RequestBody LockVoucherRequest request) {
    return Result.ok(promotionService.lock(request));
  }

  @PostMapping("/internal/confirm")
  public Result<Void> confirm(@Valid @RequestBody VoucherTransitionRequest request) {
    promotionService.confirm(request.voucherLockId(), request.orderId());
    return Result.ok();
  }

  @PostMapping("/internal/release")
  public Result<Void> release(@Valid @RequestBody VoucherTransitionRequest request) {
    promotionService.release(request.voucherLockId());
    return Result.ok();
  }

  @PostMapping("/internal/revert-confirmed")
  public Result<Void> revertConfirmed(@Valid @RequestBody VoucherTransitionRequest request) {
    promotionService.revertConfirmed(request.voucherLockId());
    return Result.ok();
  }

  @GetMapping("/internal/claims")
  public Result<List<VoucherClaimView>> claims() {
    return Result.ok(promotionService.claims());
  }

  @GetMapping("/internal/claims/retries")
  public Result<List<VoucherClaimRetryView>> claimRetries() {
    return Result.ok(promotionService.claimRetries());
  }

  @PostMapping("/internal/claims/retries/retry")
  public Result<Integer> retryClaimRetries() {
    return Result.ok(pendingRecoveryScheduler.recoverPending());
  }

  @GetMapping("/internal/locks")
  public Result<List<VoucherLockView>> locks() {
    return Result.ok(promotionService.locks());
  }

}
