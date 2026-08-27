package com.mealflow.promotion;

import com.mealflow.common.api.ErrorCode;
import com.mealflow.common.api.PageResult;
import com.mealflow.common.exception.BizException;
import com.mealflow.common.status.VoucherLockStatus;
import com.mealflow.promotion.api.LockVoucherRequest;
import com.mealflow.promotion.api.SeckillVoucherResponse;
import com.mealflow.promotion.api.UserVoucherView;
import com.mealflow.promotion.api.VoucherAdminRequest;
import com.mealflow.promotion.api.VoucherClaimRetryView;
import com.mealflow.promotion.api.VoucherClaimView;
import com.mealflow.promotion.api.VoucherLockResponse;
import com.mealflow.promotion.api.VoucherLockView;
import com.mealflow.promotion.api.VoucherView;
import com.mealflow.promotion.mapper.PromotionMapper;
import com.mealflow.promotion.mapper.UserVoucherRow;
import com.mealflow.promotion.mapper.VoucherClaimRetryRow;
import com.mealflow.promotion.mapper.VoucherClaimRow;
import com.mealflow.promotion.mapper.VoucherLockRow;
import com.mealflow.promotion.mapper.VoucherRow;
import com.mealflow.promotion.mq.SeckillClaimPublisher;
import com.mealflow.promotion.seckill.SeckillClaimCommand;
import com.mealflow.promotion.seckill.VoucherSeckillGuard;
import com.mealflow.promotion.seckill.VoucherSeckillGuard.ClaimResult;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PromotionService {
  private final PromotionMapper promotionMapper;
  private final VoucherSeckillGuard seckillGuard;
  private final SeckillClaimPublisher claimPublisher;
  private final long pendingInitialTimeoutMs;

  public PromotionService(PromotionMapper promotionMapper, VoucherSeckillGuard seckillGuard,
      SeckillClaimPublisher claimPublisher,
      @Value("${mealflow.promotion.pending-recovery.initial-timeout-ms:10000}") long pendingInitialTimeoutMs) {
    this.promotionMapper = promotionMapper;
    this.seckillGuard = seckillGuard;
    this.claimPublisher = claimPublisher;
    this.pendingInitialTimeoutMs = Math.max(1_000, pendingInitialTimeoutMs);
  }

  public SeckillVoucherResponse seckill(long userId, long voucherId, String requestId) {
    VoucherRow voucher = requireVoucher(voucherId);
    String eventKey = SeckillClaimCommand.eventKey(voucherId, userId);
    if (!"ACTIVE".equals(voucher.getStatus())) {
      return new SeckillVoucherResponse(eventKey, "FAILED", null, null);
    }
    LocalDateTime now = LocalDateTime.now();
    if (voucher.getStartTime() != null && now.isBefore(voucher.getStartTime())) {
      return new SeckillVoucherResponse(eventKey, "NOT_READY", null, null);
    }
    if (voucher.getEndTime() != null && !now.isBefore(voucher.getEndTime())) {
      return new SeckillVoucherResponse(eventKey, "FAILED", null, null);
    }

    ClaimResult claimResult;
    try {
      claimResult = seckillGuard.tryClaim(userId, voucherId,
          System.currentTimeMillis() + pendingInitialTimeoutMs);
    } catch (DataAccessException ex) {
      throw new BizException(ErrorCode.SYSTEM_ERROR, "秒杀服务暂不可用，请稍后重试");
    }
    if (claimResult == ClaimResult.SOLD_OUT) {
      return new SeckillVoucherResponse(eventKey, "SOLD_OUT", null, null);
    }
    if (claimResult == ClaimResult.NOT_READY) {
      return new SeckillVoucherResponse(eventKey, "NOT_READY", null, null);
    }
    if (claimResult == ClaimResult.DUPLICATE) {
      SeckillVoucherResponse current = claimStatus(userId, voucherId);
      if ("CLAIMED".equals(current.status())) {
        return new SeckillVoucherResponse(eventKey, "DUPLICATE", current.claimId(), current.userVoucherId());
      }
      return current;
    }

    SeckillClaimCommand command = SeckillClaimCommand.of(voucherId, userId);
    try {
      claimPublisher.publish(command);
    } catch (RuntimeException ex) {
      // The Redis reservation remains authoritative until the recovery scheduler republishes it.
      try {
        seckillGuard.delayPending(userId, voucherId, System.currentTimeMillis() + 5_000);
      } catch (RuntimeException ignored) {
        // The original Pending score is still present and will be recovered when Redis is available again.
      }
    }
    return new SeckillVoucherResponse(eventKey, "PENDING", null, null);
  }

  public SeckillVoucherResponse claimStatus(long userId, long voucherId) {
    String eventKey = SeckillClaimCommand.eventKey(voucherId, userId);
    VoucherClaimRow claim = promotionMapper.findClaim(userId, voucherId);
    if (claim != null) {
      if ("CLAIMED".equals(claim.getStatus()) || "SOLD_OUT".equals(claim.getStatus())) {
        return new SeckillVoucherResponse(eventKey, claim.getStatus(), claim.getId(), claim.getUserVoucherId());
      }
      return new SeckillVoucherResponse(eventKey, "PENDING", claim.getId(), claim.getUserVoucherId());
    }
    try {
      if (seckillGuard.isPending(userId, voucherId) || seckillGuard.isClaimed(userId, voucherId)) {
        return new SeckillVoucherResponse(eventKey, "PENDING", null, null);
      }
    } catch (DataAccessException ex) {
      throw new BizException(ErrorCode.SYSTEM_ERROR, "秒杀状态暂不可查询，请稍后重试");
    }
    return new SeckillVoucherResponse(eventKey, "NOT_FOUND", null, null);
  }

  @Transactional
  public VoucherLockResponse lock(LockVoucherRequest request) {
    if (request.userVoucherId() == null) {
      return new VoucherLockResponse(null, "SKIPPED", 0);
    }
    UserVoucherRow userVoucher = requireUserVoucher(request.userVoucherId());
    if (userVoucher.getUserId() != request.userId()) {
      throw new BizException(ErrorCode.VOUCHER_UNAVAILABLE);
    }
    VoucherRow voucher = requireVoucher(userVoucher.getVoucherId());
    if (UserVoucherStatus.LOCKED.name().equals(userVoucher.getStatus())) {
      VoucherLockRow existing = promotionMapper.findActiveLockByUserVoucherId(request.userVoucherId());
      if (existing != null) {
        return new VoucherLockResponse(existing.getId(), existing.getStatus(), voucher.getDiscountCent());
      }
    }
    if (!UserVoucherStatus.AVAILABLE.name().equals(userVoucher.getStatus())
        || promotionMapper.updateUserVoucherStatusIfCurrent(request.userVoucherId(), UserVoucherStatus.LOCKED.name(),
        UserVoucherStatus.AVAILABLE.name(), LocalDateTime.now()) != 1) {
      VoucherLockRow existing = promotionMapper.findActiveLockByUserVoucherId(request.userVoucherId());
      if (existing != null) {
        return new VoucherLockResponse(existing.getId(), existing.getStatus(), voucher.getDiscountCent());
      }
      throw new BizException(ErrorCode.VOUCHER_UNAVAILABLE);
    }
      VoucherLockRow lock = new VoucherLockRow();
      lock.setUserVoucherId(request.userVoucherId());
      lock.setStatus(VoucherLockStatus.LOCKED.name());
      lock.setTicketId(request.ticketId());
      lock.setOrderId(request.orderId());
      lock.setExpireTime(request.lockExpireTime() == null ? LocalDateTime.now().plusMinutes(15) : request.lockExpireTime());
      promotionMapper.insertLock(lock);
      return new VoucherLockResponse(lock.getId(), VoucherLockStatus.LOCKED.name(), voucher.getDiscountCent());
  }

  @Transactional
  public void confirm(Long voucherLockId, Long orderId) {
    if (voucherLockId == null) {
      return;
    }
    VoucherLock lock = requireLock(voucherLockId);
    if (promotionMapper.confirmLock(voucherLockId, VoucherLockStatus.CONFIRMED.name(), orderId,
        VoucherLockStatus.LOCKED.name(), LocalDateTime.now()) == 1) {
      promotionMapper.updateUserVoucherStatus(lock.userVoucherId(), UserVoucherStatus.USED.name(), LocalDateTime.now());
    }
  }

  @Transactional
  public void release(Long voucherLockId) {
    if (voucherLockId == null) {
      return;
    }
    VoucherLock lock = requireLock(voucherLockId);
    if (promotionMapper.releaseLock(voucherLockId, VoucherLockStatus.RELEASED.name(),
        VoucherLockStatus.LOCKED.name(), LocalDateTime.now()) == 1) {
      promotionMapper.updateUserVoucherStatus(lock.userVoucherId(), UserVoucherStatus.AVAILABLE.name(),
          LocalDateTime.now());
    }
  }

  public List<UserVoucherView> wallet(long userId) {
    return promotionMapper.findWallet(userId).stream()
        .map(voucher -> new UserVoucherView(voucher.getId(), voucher.getVoucherId(), voucher.getStatus())).toList();
  }

  public List<VoucherView> vouchers() {
    return promotionMapper.findVouchers().stream().map(this::voucherView).toList();
  }

  public PageResult<VoucherView> vouchers(int page, int pageSize) {
    int normalizedPageSize = Math.min(Math.max(pageSize, 1), 100);
    int normalizedPage = Math.max(page, 1);
    long total = promotionMapper.countVouchers();
    List<VoucherView> items = promotionMapper.findVouchersPage(normalizedPageSize, (normalizedPage - 1) * normalizedPageSize)
        .stream()
        .map(this::voucherView)
        .toList();
    return PageResult.of(items, total, normalizedPage, normalizedPageSize);
  }

  public List<VoucherView> activeVouchers() {
    return vouchers().stream().filter(voucher -> "ACTIVE".equals(voucher.status())).toList();
  }

  @Transactional
  public VoucherView createVoucher(VoucherAdminRequest request) {
    validateActivityTime(request.startTime(), request.endTime());
    VoucherRow voucher = voucherRow(null, request);
    promotionMapper.insertVoucher(voucher);
    seckillGuard.syncStock(voucher.getId(), request.stock());
    return voucherView(promotionMapper.findVoucher(voucher.getId()));
  }

  @Transactional
  public VoucherView updateVoucher(long voucherId, VoucherAdminRequest request) {
    VoucherRow existing = requireVoucher(voucherId);
    validateActivityTime(request.startTime(), request.endTime());
    boolean stockChanged = existing.getStock() != request.stock();
    if (stockChanged && (existing.getStartTime() == null || !LocalDateTime.now().isBefore(existing.getStartTime())
        || promotionMapper.countVoucherClaims(voucherId) > 0)) {
      throw new BizException(ErrorCode.ILLEGAL_STATUS, "活动开始或已有领取后不能直接修改库存");
    }
    VoucherRow voucher = voucherRow(voucherId, request);
    promotionMapper.updateVoucher(voucher);
    if (stockChanged) {
      seckillGuard.syncStock(voucherId, request.stock());
    }
    return voucherView(promotionMapper.findVoucher(voucherId));
  }

  public List<VoucherClaimView> claims() {
    return promotionMapper.findClaims().stream()
        .map(claim -> new VoucherClaimView(claim.getId(), claim.getUserId(), claim.getVoucherId(), claim.getStatus()))
        .toList();
  }

  public List<VoucherClaimRetryView> claimRetries() {
    return promotionMapper.findClaimRetries().stream().map(this::claimRetryView).toList();
  }

  public List<VoucherLockView> locks() {
    return promotionMapper.findLocks().stream()
        .map(lock -> new VoucherLockView(lock.getId(), lock.getUserVoucherId(), lock.getStatus(), lock.getTicketId(),
            lock.getOrderId())).toList();
  }

  @Scheduled(initialDelayString = "${mealflow.promotion.lock-expire.initial-delay-ms:30000}",
      fixedDelayString = "${mealflow.promotion.lock-expire.fixed-delay-ms:30000}")
  @Transactional
  public void expireLocks() {
    LocalDateTime now = LocalDateTime.now();
    for (VoucherLockRow lock : promotionMapper.findExpiredLocks(now, 100)) {
      if (promotionMapper.expireLock(lock.getId(), VoucherLockStatus.EXPIRED.name(), now) == 1) {
        promotionMapper.updateUserVoucherStatusIfCurrent(lock.getUserVoucherId(), UserVoucherStatus.AVAILABLE.name(),
            UserVoucherStatus.LOCKED.name(), now);
      }
    }
  }

  private VoucherRow requireVoucher(long voucherId) {
    VoucherRow voucher = promotionMapper.findVoucher(voucherId);
    if (voucher == null) {
      throw new BizException(ErrorCode.NOT_FOUND, "voucher not found");
    }
    return voucher;
  }

  private UserVoucherRow requireUserVoucher(long userVoucherId) {
    UserVoucherRow userVoucher = promotionMapper.findUserVoucher(userVoucherId);
    if (userVoucher == null) {
      throw new BizException(ErrorCode.NOT_FOUND, "user voucher not found");
    }
    return userVoucher;
  }

  private VoucherLock requireLock(long voucherLockId) {
    VoucherLockRow lock = promotionMapper.findLock(voucherLockId);
    if (lock == null) {
      throw new BizException(ErrorCode.NOT_FOUND, "voucher lock not found");
    }
    return new VoucherLock(lock.getId(), lock.getUserVoucherId(), lock.getStatus(), lock.getTicketId(),
        lock.getOrderId());
  }

  private VoucherRow voucherRow(Long id, VoucherAdminRequest request) {
    VoucherRow voucher = new VoucherRow();
    if (id != null) {
      voucher.setId(id);
    }
    voucher.setName(request.name());
    voucher.setType(voucherType(request.type()));
    voucher.setDiscountCent(request.discountCent());
    voucher.setStock(request.stock());
    voucher.setStatus(voucherStatus(request.status()));
    voucher.setStartTime(request.startTime());
    voucher.setEndTime(request.endTime());
    return voucher;
  }

  private VoucherView voucherView(VoucherRow voucher) {
    return new VoucherView(voucher.getId(), voucher.getName(), voucher.getType(), voucher.getDiscountCent(),
        voucher.getStock(), voucher.getStatus(), voucher.getStartTime(), voucher.getEndTime());
  }

  private VoucherClaimRetryView claimRetryView(VoucherClaimRetryRow retry) {
    return new VoucherClaimRetryView(retry.getId(), retry.getEventKey(), retry.getUserId(), retry.getVoucherId(),
        retry.getStatus(), retry.getRetryCount(), retry.getLastError(), retry.getNextRetryTime());
  }

  private void validateActivityTime(LocalDateTime startTime, LocalDateTime endTime) {
    if (startTime != null && endTime != null && !startTime.isBefore(endTime)) {
      throw new BizException(ErrorCode.BAD_REQUEST, "活动结束时间必须晚于开始时间");
    }
  }

  private String voucherType(String type) {
    if (!"SECKILL".equals(type)) {
      throw new BizException(ErrorCode.BAD_REQUEST, "voucher type must be SECKILL");
    }
    return type;
  }

  private String voucherStatus(String status) {
    String value = status == null || status.isBlank() ? "ACTIVE" : status;
    if (!List.of("ACTIVE", "DISABLED").contains(value)) {
      throw new BizException(ErrorCode.BAD_REQUEST, "voucher status must be ACTIVE or DISABLED");
    }
    return value;
  }

  enum UserVoucherStatus {
    AVAILABLE, LOCKED, USED
  }

  record VoucherLock(long id, long userVoucherId, String status, Long ticketId, Long orderId) {
  }
}
