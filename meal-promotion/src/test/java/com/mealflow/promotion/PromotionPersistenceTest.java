package com.mealflow.promotion;

import static org.assertj.core.api.Assertions.assertThat;

import com.mealflow.promotion.api.LockVoucherRequest;
import com.mealflow.promotion.api.SeckillVoucherResponse;
import com.mealflow.promotion.api.VoucherLockResponse;
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
}
