package com.mealflow.promotion;

import static org.assertj.core.api.Assertions.assertThat;

import com.mealflow.promotion.api.LockVoucherRequest;
import com.mealflow.promotion.api.SeckillVoucherResponse;
import com.mealflow.promotion.api.VoucherLockResponse;
import com.mealflow.promotion.mapper.PromotionMapper;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = "spring.cloud.nacos.discovery.enabled=false"
)
class PromotionPersistenceTest {
  @Autowired
  private PromotionService promotionService;

  @Autowired
  private PromotionMapper promotionMapper;

  @Test
  void claimsLocksAndConfirmsVoucherInDatabase() {
    SeckillVoucherResponse claim = promotionService.seckill(201L, 1000L, "promotion-test-claim");

    assertThat(claim.status()).isEqualTo("CLAIMED");
    assertThat(claim.userVoucherId()).isNotNull();

    VoucherLockResponse lock = promotionService.lock(new LockVoucherRequest("promotion-test-lock", 201L,
        claim.userVoucherId(), null, null, LocalDateTime.now().plusMinutes(10)));

    assertThat(lock.status()).isEqualTo("LOCKED");
    assertThat(lock.discountAmount()).isEqualTo(500);

    promotionService.confirm(lock.voucherLockId(), 10001L);

    assertThat(promotionService.wallet(201L))
        .anySatisfy(voucher -> assertThat(voucher.status()).isEqualTo("USED"));
    assertThat(promotionService.locks())
        .anySatisfy(voucherLock -> {
          assertThat(voucherLock.voucherLockId()).isEqualTo(lock.voucherLockId());
          assertThat(voucherLock.status()).isEqualTo("CONFIRMED");
          assertThat(voucherLock.orderId()).isEqualTo(10001L);
        });
  }

  @Test
  void rejectsDuplicateVoucherClaimForSameUser() {
    SeckillVoucherResponse first = promotionService.seckill(202L, 1000L, "promotion-test-duplicate-1");
    SeckillVoucherResponse duplicate = promotionService.seckill(202L, 1000L, "promotion-test-duplicate-2");

    assertThat(first.status()).isEqualTo("CLAIMED");
    assertThat(duplicate.status()).isEqualTo("DUPLICATE");
    assertThat(promotionService.wallet(202L))
        .filteredOn(voucher -> voucher.voucherId() == 1000L)
        .hasSize(1);
  }

  @Test
  void repairsPendingVoucherClaimRetry() {
    LocalDateTime now = LocalDateTime.now();
    promotionMapper.insertClaimRetry(promotionMapper.maxVoucherClaimRetryId() + 100,
        203L, 1000L, "PENDING", 0, 3, "REDIS_ACCEPTED_DB_MISSING", now.minusSeconds(1), now);

    int repaired = promotionService.retryClaimRetries(10);

    assertThat(repaired).isEqualTo(1);
    assertThat(promotionService.wallet(203L))
        .filteredOn(voucher -> voucher.voucherId() == 1000L)
        .singleElement()
        .satisfies(voucher -> assertThat(voucher.status()).isEqualTo("AVAILABLE"));
    assertThat(promotionService.claimRetries())
        .filteredOn(retry -> retry.userId() == 203L && retry.voucherId() == 1000L)
        .singleElement()
        .satisfies(retry -> assertThat(retry.status()).isEqualTo("REPAIRED"));
  }

  @Test
  void movesClaimRetryToDeadAfterMaxAttempts() {
    LocalDateTime now = LocalDateTime.now();
    promotionMapper.insertClaimRetry(promotionMapper.maxVoucherClaimRetryId() + 200,
        204L, 999999L, "RETRY", 2, 3, "previous failure", now.minusSeconds(1), now);

    int repaired = promotionService.retryClaimRetries(10);

    assertThat(repaired).isZero();
    assertThat(promotionService.claimRetries())
        .filteredOn(retry -> retry.userId() == 204L && retry.voucherId() == 999999L)
        .singleElement()
        .satisfies(retry -> {
          assertThat(retry.status()).isEqualTo("DEAD");
          assertThat(retry.retryCount()).isEqualTo(3);
          assertThat(retry.lastError()).contains("voucher not found");
        });
  }
}
