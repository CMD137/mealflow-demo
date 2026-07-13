package com.mealflow.promotion;

import com.mealflow.common.api.ErrorCode;
import com.mealflow.common.exception.BizException;
import com.mealflow.common.status.VoucherClaimStatus;
import com.mealflow.common.status.VoucherLockStatus;
import com.mealflow.infra.id.IdGenerator;
import com.mealflow.infra.idempotent.IdempotentTemplate;
import com.mealflow.promotion.api.LockVoucherRequest;
import com.mealflow.promotion.api.SeckillVoucherResponse;
import com.mealflow.promotion.api.UserVoucherView;
import com.mealflow.promotion.api.VoucherAdminRequest;
import com.mealflow.promotion.api.VoucherClaimView;
import com.mealflow.promotion.api.VoucherClaimRetryView;
import com.mealflow.promotion.api.VoucherLockResponse;
import com.mealflow.promotion.api.VoucherLockView;
import com.mealflow.promotion.api.VoucherView;
import com.mealflow.promotion.mapper.PromotionMapper;
import com.mealflow.promotion.mapper.VoucherClaimRetryRow;
import com.mealflow.promotion.mapper.UserVoucherRow;
import com.mealflow.promotion.mapper.VoucherClaimRow;
import com.mealflow.promotion.mapper.VoucherLockRow;
import com.mealflow.promotion.mapper.VoucherRow;
import com.mealflow.promotion.seckill.VoucherSeckillGuard;
import com.mealflow.promotion.seckill.VoucherSeckillGuard.ClaimResult;
import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PromotionService {
  private final IdGenerator idGenerator = new IdGenerator();
  private final IdempotentTemplate idempotentTemplate = new IdempotentTemplate();
  private final PromotionMapper promotionMapper;
  private final VoucherSeckillGuard seckillGuard;
  private final int claimRetryMaxRetries;

  public PromotionService(PromotionMapper promotionMapper, VoucherSeckillGuard seckillGuard,
      @Value("${mealflow.promotion.claim-retry.max-retries:3}") int claimRetryMaxRetries) {
    this.promotionMapper = promotionMapper;
    this.seckillGuard = seckillGuard;
    this.claimRetryMaxRetries = Math.max(1, claimRetryMaxRetries);
  }

  @PostConstruct
  void initializeIdGenerator() {
    ensureVoucherColumns();
    promotionMapper.createClaimRetryTable();
    idGenerator.ensureAtLeast("voucher", promotionMapper.maxVoucherId());
    idGenerator.ensureAtLeast("userVoucher", promotionMapper.maxUserVoucherId());
    idGenerator.ensureAtLeast("voucherLock", promotionMapper.maxVoucherLockId());
    idGenerator.ensureAtLeast("voucherClaim", promotionMapper.maxVoucherClaimId());
    idGenerator.ensureAtLeast("voucherClaimRetry", promotionMapper.maxVoucherClaimRetryId());
  }

  @Transactional
  public synchronized SeckillVoucherResponse seckill(long userId, long voucherId, String requestId) {
    return idempotentTemplate.execute("promotion:claim:" + userId + ":" + requestId, () -> {
      VoucherRow voucher = requireVoucher(voucherId);
      if (!"ACTIVE".equals(voucher.getStatus())) {
        throw new BizException(ErrorCode.VOUCHER_UNAVAILABLE, "voucher is not active");
      }
      ClaimResult claimResult = seckillGuard.tryClaim(userId, voucherId, voucher.getStock());
      if (claimResult == ClaimResult.DUPLICATE) {
        long claimId = insertClaim(userId, voucherId, VoucherClaimStatus.DUPLICATE);
        return new SeckillVoucherResponse(claimId, "DUPLICATE", null);
      }
      if (claimResult == ClaimResult.SOLD_OUT) {
        return new SeckillVoucherResponse(null, "SOLD_OUT", null);
      }
      try {
        long userVoucherId = idGenerator.next("userVoucher");
        LocalDateTime now = LocalDateTime.now();
        promotionMapper.insertUserVoucher(userVoucherId, userId, voucherId, UserVoucherStatus.AVAILABLE.name(), now);
        long claimId = insertClaim(userId, voucherId, VoucherClaimStatus.CLAIMED);
        return new SeckillVoucherResponse(claimId, "CLAIMED", userVoucherId);
      } catch (DuplicateKeyException ex) {
        seckillGuard.compensate(userId, voucherId);
        long claimId = insertClaim(userId, voucherId, VoucherClaimStatus.DUPLICATE);
        return new SeckillVoucherResponse(claimId, "DUPLICATE", null);
      } catch (RuntimeException ex) {
        seckillGuard.compensate(userId, voucherId);
        throw ex;
      }
    });
  }

  @Transactional
  public VoucherLockResponse lock(LockVoucherRequest request) {
    if (request.userVoucherId() == null) {
      return new VoucherLockResponse(null, "SKIPPED", 0);
    }
    return idempotentTemplate.execute("promotion:lock:" + request.userId() + ":" + request.requestId(), () -> {
      synchronized (this) {
        UserVoucherRow userVoucher = requireUserVoucher(request.userVoucherId());
        if (userVoucher.getUserId() != request.userId()
            || UserVoucherStatus.valueOf(userVoucher.getStatus()) != UserVoucherStatus.AVAILABLE) {
          throw new BizException(ErrorCode.VOUCHER_UNAVAILABLE);
        }
        int affected = promotionMapper.updateUserVoucherStatusIfCurrent(request.userVoucherId(),
            UserVoucherStatus.LOCKED.name(), UserVoucherStatus.AVAILABLE.name(), LocalDateTime.now());
        if (affected != 1) {
          throw new BizException(ErrorCode.VOUCHER_UNAVAILABLE);
        }
        VoucherRow voucher = requireVoucher(userVoucher.getVoucherId());
        long lockId = idGenerator.next("voucherLock");
        promotionMapper.insertLock(lockId, request.userVoucherId(), VoucherLockStatus.LOCKED.name(),
            request.ticketId(), request.orderId(), LocalDateTime.now());
        return new VoucherLockResponse(lockId, VoucherLockStatus.LOCKED.name(), voucher.getDiscountCent());
      }
    });
  }

  @Transactional
  public synchronized void confirm(Long voucherLockId, Long orderId) {
    if (voucherLockId == null) {
      return;
    }
    VoucherLock lock = requireLock(voucherLockId);
    if (VoucherLockStatus.valueOf(lock.status()) == VoucherLockStatus.LOCKED) {
      promotionMapper.confirmLock(voucherLockId, VoucherLockStatus.CONFIRMED.name(), orderId,
          VoucherLockStatus.LOCKED.name(), LocalDateTime.now());
      promotionMapper.updateUserVoucherStatus(lock.userVoucherId(), UserVoucherStatus.USED.name(),
          LocalDateTime.now());
    }
  }

  @Transactional
  public synchronized void release(Long voucherLockId) {
    if (voucherLockId == null) {
      return;
    }
    VoucherLock lock = requireLock(voucherLockId);
    if (VoucherLockStatus.valueOf(lock.status()) == VoucherLockStatus.LOCKED) {
      promotionMapper.releaseLock(voucherLockId, VoucherLockStatus.RELEASED.name(),
          VoucherLockStatus.LOCKED.name(), LocalDateTime.now());
      promotionMapper.updateUserVoucherStatus(lock.userVoucherId(), UserVoucherStatus.AVAILABLE.name(),
          LocalDateTime.now());
    }
  }

  public List<UserVoucherView> wallet(long userId) {
    return promotionMapper.findWallet(userId).stream()
        .map(voucher -> new UserVoucherView(voucher.getId(), voucher.getVoucherId(), voucher.getStatus()))
        .toList();
  }

  public List<VoucherView> vouchers() {
    return promotionMapper.findVouchers().stream().map(this::voucherView).toList();
  }

  @Transactional
  public synchronized VoucherView createVoucher(VoucherAdminRequest request) {
    long id = idGenerator.next("voucher");
    LocalDateTime now = LocalDateTime.now();
    promotionMapper.insertVoucher(id, request.name(), voucherType(request.type()), request.discountCent(),
        request.stock(), voucherStatus(request.status()), now);
    seckillGuard.syncStock(id, request.stock());
    return voucherView(promotionMapper.findVoucher(id));
  }

  @Transactional
  public synchronized VoucherView updateVoucher(long voucherId, VoucherAdminRequest request) {
    requireVoucher(voucherId);
    promotionMapper.updateVoucher(voucherId, request.name(), voucherType(request.type()), request.discountCent(),
        request.stock(), voucherStatus(request.status()), LocalDateTime.now());
    seckillGuard.syncStock(voucherId, request.stock());
    return voucherView(promotionMapper.findVoucher(voucherId));
  }

  public List<VoucherClaimView> claims() {
    return promotionMapper.findClaims().stream().map(this::claimView).toList();
  }

  public List<VoucherClaimRetryView> claimRetries() {
    return promotionMapper.findClaimRetries().stream().map(this::claimRetryView).toList();
  }

  public List<VoucherLockView> locks() {
    return promotionMapper.findLocks().stream().map(this::lockView).toList();
  }

  @Transactional
  public synchronized int reconcileRedisClaims() {
    int repaired = 0;
    for (VoucherRow voucher : promotionMapper.findVouchers()) {
      repaired += reconcileRedisClaims(voucher.getId());
    }
    return repaired;
  }

  @Transactional
  public synchronized int reconcileRedisClaims(long voucherId) {
    requireVoucher(voucherId);
    discoverRedisClaimRetries(voucherId);
    return retryClaimRetries(100);
  }

  @Transactional
  public synchronized int retryClaimRetries(int limit) {
    int repaired = 0;
    for (VoucherClaimRetryRow row : promotionMapper.findDueClaimRetries(LocalDateTime.now(), limit)) {
      if (repairClaimRetry(row)) {
        repaired++;
      }
    }
    return repaired;
  }

  private int discoverRedisClaimRetries(long voucherId) {
    int discovered = 0;
    for (Long userId : seckillGuard.claimedUsers(voucherId)) {
      if (promotionMapper.countUserVoucher(userId, voucherId) > 0) {
        continue;
      }
      enqueueClaimRetry(userId, voucherId, "REDIS_ACCEPTED_DB_MISSING");
      discovered++;
    }
    return discovered;
  }

  private void enqueueClaimRetry(long userId, long voucherId, String lastError) {
    LocalDateTime now = LocalDateTime.now();
    VoucherClaimRetryRow existing = promotionMapper.findClaimRetry(userId, voucherId);
    if (existing == null) {
      promotionMapper.insertClaimRetry(idGenerator.next("voucherClaimRetry"), userId, voucherId,
          ClaimRetryStatus.PENDING.name(), 0, claimRetryMaxRetries, lastError, now, now);
      return;
    }
    if (ClaimRetryStatus.REPAIRED.name().equals(existing.getStatus())) {
      return;
    }
    promotionMapper.touchClaimRetry(userId, voucherId, ClaimRetryStatus.PENDING.name(), lastError, now, now);
  }

  private boolean repairClaimRetry(VoucherClaimRetryRow row) {
    LocalDateTime now = LocalDateTime.now();
    try {
      requireVoucher(row.getVoucherId());
      if (promotionMapper.countUserVoucher(row.getUserId(), row.getVoucherId()) == 0) {
        long userVoucherId = idGenerator.next("userVoucher");
        promotionMapper.insertUserVoucher(userVoucherId, row.getUserId(), row.getVoucherId(),
            UserVoucherStatus.AVAILABLE.name(), now);
        insertClaim(row.getUserId(), row.getVoucherId(), VoucherClaimStatus.CLAIMED);
      }
      promotionMapper.updateClaimRetry(row.getId(), ClaimRetryStatus.REPAIRED.name(), row.getRetryCount(), null, now,
          now);
      return true;
    } catch (DuplicateKeyException ex) {
      promotionMapper.updateClaimRetry(row.getId(), ClaimRetryStatus.REPAIRED.name(), row.getRetryCount(), null, now,
          now);
      return true;
    } catch (RuntimeException ex) {
      int retryCount = row.getRetryCount() + 1;
      String status = retryCount >= row.getMaxRetries()
          ? ClaimRetryStatus.DEAD.name()
          : ClaimRetryStatus.RETRY.name();
      promotionMapper.updateClaimRetry(row.getId(), status, retryCount, trimError(ex),
          now.plusSeconds(30L * retryCount), now);
      return false;
    }
  }

  private long insertClaim(long userId, long voucherId, VoucherClaimStatus status) {
    long claimId = idGenerator.next("voucherClaim");
    LocalDateTime now = LocalDateTime.now();
    promotionMapper.insertClaim(claimId, userId, voucherId, status.name(), now);
    return claimId;
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

  private VoucherClaimView claimView(VoucherClaimRow claim) {
    return new VoucherClaimView(claim.getId(), claim.getUserId(), claim.getVoucherId(), claim.getStatus());
  }

  private VoucherView voucherView(VoucherRow voucher) {
    return new VoucherView(voucher.getId(), voucher.getName(), voucher.getType(), voucher.getDiscountCent(),
        seckillGuard.remainingStock(voucher.getId(), voucher.getStock()), voucher.getStatus());
  }

  private VoucherClaimRetryView claimRetryView(VoucherClaimRetryRow retry) {
    return new VoucherClaimRetryView(retry.getId(), retry.getUserId(), retry.getVoucherId(), retry.getStatus(),
        retry.getRetryCount(), retry.getMaxRetries(), retry.getLastError(), retry.getNextRetryTime());
  }

  private VoucherLockView lockView(VoucherLockRow lock) {
    return new VoucherLockView(lock.getId(), lock.getUserVoucherId(), lock.getStatus(), lock.getTicketId(),
        lock.getOrderId());
  }

  private String trimError(RuntimeException ex) {
    String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
    return message.length() <= 512 ? message : message.substring(0, 512);
  }

  private String voucherType(String type) {
    if (type == null || type.isBlank()) {
      return "SECKILL";
    }
    if (!"SECKILL".equals(type)) {
      throw new BizException(ErrorCode.BAD_REQUEST, "voucher type must be SECKILL");
    }
    return "SECKILL";
  }

  private String voucherStatus(String status) {
    if (status == null || status.isBlank()) {
      return "ACTIVE";
    }
    if (!List.of("ACTIVE", "DISABLED").contains(status)) {
      throw new BizException(ErrorCode.BAD_REQUEST, "voucher status must be ACTIVE or DISABLED");
    }
    return status;
  }

  private void ensureVoucherColumns() {
    if (promotionMapper.countVoucherColumn("name") == 0) {
      promotionMapper.addVoucherNameColumn();
    }
    if (promotionMapper.countVoucherColumn("type") == 0) {
      promotionMapper.addVoucherTypeColumn();
    }
    if (promotionMapper.countVoucherColumn("status") == 0) {
      promotionMapper.addVoucherStatusColumn();
    }
    if (promotionMapper.countVoucherColumn("start_time") == 0) {
      promotionMapper.addVoucherStartTimeColumn();
    }
    if (promotionMapper.countVoucherColumn("end_time") == 0) {
      promotionMapper.addVoucherEndTimeColumn();
    }
    if (promotionMapper.countVoucherColumn("create_time") == 0) {
      promotionMapper.addVoucherCreateTimeColumn();
    }
    if (promotionMapper.countVoucherColumn("update_time") == 0) {
      promotionMapper.addVoucherUpdateTimeColumn();
    }
    promotionMapper.hydrateSeedVoucherMetadata();
  }

  enum UserVoucherStatus {
    AVAILABLE, LOCKED, USED
  }

  enum ClaimRetryStatus {
    PENDING, RETRY, REPAIRED, DEAD
  }

  record VoucherLock(long id, long userVoucherId, String status, Long ticketId, Long orderId) {
  }
}
