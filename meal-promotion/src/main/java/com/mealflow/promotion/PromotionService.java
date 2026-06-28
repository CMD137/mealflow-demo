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
import com.mealflow.promotion.api.VoucherClaimView;
import com.mealflow.promotion.api.VoucherLockResponse;
import com.mealflow.promotion.api.VoucherLockView;
import com.mealflow.promotion.mapper.PromotionMapper;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PromotionService {
  private final IdGenerator idGenerator = new IdGenerator();
  private final IdempotentTemplate idempotentTemplate = new IdempotentTemplate();
  private final PromotionMapper promotionMapper;
  private final VoucherSeckillGuard seckillGuard;

  public PromotionService(PromotionMapper promotionMapper, VoucherSeckillGuard seckillGuard) {
    this.promotionMapper = promotionMapper;
    this.seckillGuard = seckillGuard;
  }

  @PostConstruct
  void initializeIdGenerator() {
    idGenerator.ensureAtLeast("userVoucher", promotionMapper.maxUserVoucherId());
    idGenerator.ensureAtLeast("voucherLock", promotionMapper.maxVoucherLockId());
    idGenerator.ensureAtLeast("voucherClaim", promotionMapper.maxVoucherClaimId());
  }

  @Transactional
  public synchronized SeckillVoucherResponse seckill(long userId, long voucherId, String requestId) {
    return idempotentTemplate.execute("promotion:claim:" + userId + ":" + requestId, () -> {
      VoucherRow voucher = requireVoucher(voucherId);
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

  public List<VoucherClaimView> claims() {
    return promotionMapper.findClaims().stream().map(this::claimView).toList();
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
    int repaired = 0;
    for (Long userId : seckillGuard.claimedUsers(voucherId)) {
      if (promotionMapper.countUserVoucher(userId, voucherId) > 0) {
        continue;
      }
      long userVoucherId = idGenerator.next("userVoucher");
      LocalDateTime now = LocalDateTime.now();
      promotionMapper.insertUserVoucher(userVoucherId, userId, voucherId, UserVoucherStatus.AVAILABLE.name(), now);
      insertClaim(userId, voucherId, VoucherClaimStatus.CLAIMED);
      repaired++;
    }
    return repaired;
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

  private VoucherLockView lockView(VoucherLockRow lock) {
    return new VoucherLockView(lock.getId(), lock.getUserVoucherId(), lock.getStatus(), lock.getTicketId(),
        lock.getOrderId());
  }

  enum UserVoucherStatus {
    AVAILABLE, LOCKED, USED
  }

  record VoucherLock(long id, long userVoucherId, String status, Long ticketId, Long orderId) {
  }
}
