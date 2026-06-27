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
import jakarta.annotation.PostConstruct;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class PromotionService {
  private final IdGenerator idGenerator = new IdGenerator();
  private final IdempotentTemplate idempotentTemplate = new IdempotentTemplate();
  private final Map<Long, Voucher> vouchers = new ConcurrentHashMap<>();
  private final Map<Long, UserVoucher> userVouchers = new ConcurrentHashMap<>();
  private final Map<Long, VoucherClaim> claims = new ConcurrentHashMap<>();
  private final Map<Long, VoucherLock> locks = new ConcurrentHashMap<>();
  private final Set<String> claimedUsers = ConcurrentHashMap.newKeySet();

  @PostConstruct
  void seed() {
    vouchers.put(1000L, new Voucher(1000L, 500, 100));
    userVouchers.put(300L, new UserVoucher(300L, 100L, 1000L, UserVoucherStatus.AVAILABLE));
    claimedUsers.add("100:1000");
  }

  public synchronized SeckillVoucherResponse seckill(long userId, long voucherId, String requestId) {
    return idempotentTemplate.execute("promotion:claim:" + userId + ":" + requestId, () -> {
      Voucher voucher = requireVoucher(voucherId);
      String userVoucherKey = userId + ":" + voucherId;
      if (claimedUsers.contains(userVoucherKey)) {
        long claimId = idGenerator.next("voucherClaim");
        claims.put(claimId, new VoucherClaim(claimId, userId, voucherId, VoucherClaimStatus.DUPLICATE));
        return new SeckillVoucherResponse(claimId, "DUPLICATE", null);
      }
      if (voucher.stock <= 0) {
        return new SeckillVoucherResponse(null, "SOLD_OUT", null);
      }
      voucher.stock -= 1;
      claimedUsers.add(userVoucherKey);
      long claimId = idGenerator.next("voucherClaim");
      long userVoucherId = idGenerator.next("userVoucher");
      claims.put(claimId, new VoucherClaim(claimId, userId, voucherId, VoucherClaimStatus.CLAIMED));
      userVouchers.put(userVoucherId, new UserVoucher(userVoucherId, userId, voucherId, UserVoucherStatus.AVAILABLE));
      return new SeckillVoucherResponse(claimId, "CLAIMED", userVoucherId);
    });
  }

  public VoucherLockResponse lock(LockVoucherRequest request) {
    if (request.userVoucherId() == null) {
      return new VoucherLockResponse(null, "SKIPPED", 0);
    }
    return idempotentTemplate.execute("promotion:lock:" + request.userId() + ":" + request.requestId(), () -> {
      synchronized (this) {
        UserVoucher userVoucher = requireUserVoucher(request.userVoucherId());
        if (userVoucher.userId != request.userId() || userVoucher.status != UserVoucherStatus.AVAILABLE) {
          throw new BizException(ErrorCode.VOUCHER_UNAVAILABLE);
        }
        userVoucher.status = UserVoucherStatus.LOCKED;
        Voucher voucher = requireVoucher(userVoucher.voucherId);
        long lockId = idGenerator.next("voucherLock");
        locks.put(lockId, new VoucherLock(lockId, request.userVoucherId(), VoucherLockStatus.LOCKED,
            request.ticketId(), request.orderId()));
        return new VoucherLockResponse(lockId, VoucherLockStatus.LOCKED.name(), voucher.discountCent);
      }
    });
  }

  public synchronized void confirm(Long voucherLockId, Long orderId) {
    if (voucherLockId == null) {
      return;
    }
    VoucherLock lock = requireLock(voucherLockId);
    if (lock.status == VoucherLockStatus.LOCKED) {
      lock.status = VoucherLockStatus.CONFIRMED;
      lock.orderId = orderId;
      requireUserVoucher(lock.userVoucherId).status = UserVoucherStatus.USED;
    }
  }

  public synchronized void release(Long voucherLockId) {
    if (voucherLockId == null) {
      return;
    }
    VoucherLock lock = requireLock(voucherLockId);
    if (lock.status == VoucherLockStatus.LOCKED) {
      lock.status = VoucherLockStatus.RELEASED;
      requireUserVoucher(lock.userVoucherId).status = UserVoucherStatus.AVAILABLE;
    }
  }

  public List<UserVoucherView> wallet(long userId) {
    return userVouchers.values().stream()
        .filter(voucher -> voucher.userId == userId)
        .sorted(Comparator.comparingLong(voucher -> voucher.id))
        .map(voucher -> new UserVoucherView(voucher.id, voucher.voucherId, voucher.status.name()))
        .toList();
  }

  public List<VoucherClaimView> claims() {
    return claims.values().stream()
        .sorted(Comparator.comparingLong(claim -> claim.id))
        .map(claim -> new VoucherClaimView(claim.id, claim.userId, claim.voucherId, claim.status.name()))
        .toList();
  }

  public List<VoucherLockView> locks() {
    return locks.values().stream()
        .sorted(Comparator.comparingLong(lock -> lock.id))
        .map(lock -> new VoucherLockView(lock.id, lock.userVoucherId, lock.status.name(), lock.ticketId, lock.orderId))
        .toList();
  }

  private Voucher requireVoucher(long voucherId) {
    Voucher voucher = vouchers.get(voucherId);
    if (voucher == null) {
      throw new BizException(ErrorCode.NOT_FOUND, "优惠券不存在");
    }
    return voucher;
  }

  private UserVoucher requireUserVoucher(long userVoucherId) {
    UserVoucher userVoucher = userVouchers.get(userVoucherId);
    if (userVoucher == null) {
      throw new BizException(ErrorCode.NOT_FOUND, "用户券不存在");
    }
    return userVoucher;
  }

  private VoucherLock requireLock(long voucherLockId) {
    VoucherLock lock = locks.get(voucherLockId);
    if (lock == null) {
      throw new BizException(ErrorCode.NOT_FOUND, "优惠券锁不存在");
    }
    return lock;
  }

  static class Voucher {
    final long id;
    final int discountCent;
    int stock;

    Voucher(long id, int discountCent, int stock) {
      this.id = id;
      this.discountCent = discountCent;
      this.stock = stock;
    }
  }

  static class UserVoucher {
    final long id;
    final long userId;
    final long voucherId;
    UserVoucherStatus status;

    UserVoucher(long id, long userId, long voucherId, UserVoucherStatus status) {
      this.id = id;
      this.userId = userId;
      this.voucherId = voucherId;
      this.status = status;
    }
  }

  enum UserVoucherStatus {
    AVAILABLE, LOCKED, USED
  }

  record VoucherClaim(long id, long userId, long voucherId, VoucherClaimStatus status) {
  }

  static class VoucherLock {
    final long id;
    final long userVoucherId;
    VoucherLockStatus status;
    Long ticketId;
    Long orderId;

    VoucherLock(long id, long userVoucherId, VoucherLockStatus status, Long ticketId, Long orderId) {
      this.id = id;
      this.userVoucherId = userVoucherId;
      this.status = status;
      this.ticketId = ticketId;
      this.orderId = orderId;
    }
  }
}
