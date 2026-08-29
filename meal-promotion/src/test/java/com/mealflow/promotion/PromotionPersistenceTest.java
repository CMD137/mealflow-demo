package com.mealflow.promotion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mealflow.common.exception.BizException;
import com.mealflow.promotion.api.SeckillVoucherResponse;
import com.mealflow.promotion.api.LockVoucherRequest;
import com.mealflow.promotion.api.VoucherAdminRequest;
import com.mealflow.promotion.api.VoucherView;
import com.mealflow.promotion.mapper.PromotionMapper;
import com.mealflow.promotion.mq.SeckillClaimPublisher;
import com.mealflow.promotion.mq.SeckillClaimRocketMqConsumer;
import com.mealflow.promotion.seckill.ClaimSettlementResult;
import com.mealflow.promotion.seckill.SeckillClaimCommand;
import com.mealflow.promotion.seckill.VoucherClaimSettlementService;
import com.mealflow.promotion.seckill.VoucherClaimPendingRecoveryScheduler;
import com.mealflow.promotion.seckill.VoucherSeckillGuard;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
    "spring.cloud.nacos.discovery.enabled=false",
    "mealflow.mq.seckill-consumer.enabled=false",
    "mealflow.promotion.pending-recovery.enabled=false"
})
class PromotionPersistenceTest {
  @Autowired
  private PromotionService promotionService;

  @Autowired
  private VoucherClaimSettlementService settlementService;

  @Autowired
  private PromotionMapper promotionMapper;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private VoucherClaimPendingRecoveryScheduler recoveryScheduler;

  @MockBean
  private VoucherSeckillGuard seckillGuard;

  @MockBean
  private SeckillClaimPublisher claimPublisher;

  @BeforeEach
  void assumeContinuousRedisSeckillState() {
    when(seckillGuard.isStateInitialized()).thenReturn(true);
  }

  @Test
  void acceptsRedisReservationAndReturnsPending() {
    when(seckillGuard.tryClaim(anyLong(), anyLong(), anyLong()))
        .thenReturn(VoucherSeckillGuard.ClaimResult.ACCEPTED);

    SeckillVoucherResponse response = promotionService.seckill(201L, 1000L, "request-is-not-the-event-key");

    assertThat(response.eventKey()).isEqualTo("seckill:1000:201");
    assertThat(response.status()).isEqualTo("PENDING");
    verify(claimPublisher).publish(SeckillClaimCommand.of(1000L, 201L));
  }

  @Test
  void repeatedMessageOnlyDeductsMysqlStockOnce() {
    VoucherView voucher = newVoucher(3);
    SeckillClaimCommand command = SeckillClaimCommand.of(voucher.voucherId(), 202L);

    ClaimSettlementResult first = settlementService.settle(command);
    ClaimSettlementResult duplicate = settlementService.settle(command);

    assertThat(first.status()).isEqualTo("CLAIMED");
    assertThat(duplicate).isEqualTo(first);
    assertThat(promotionMapper.findVoucher(voucher.voucherId()).getStock()).isEqualTo(2);
    assertThat(promotionMapper.countUserVoucher(202L, voucher.voucherId())).isEqualTo(1);
  }

  @Test
  void releasesAnExpiredVoucherLockWithAStatusCondition() {
    VoucherView voucher = newVoucher(1);
    ClaimSettlementResult claim = settlementService.settle(SeckillClaimCommand.of(voucher.voucherId(), 20_201L));
    promotionService.lock(new LockVoucherRequest("voucher-lock-expire-1", 20_201L, claim.userVoucherId(), null,
        null, LocalDateTime.now().minusMinutes(1)));

    promotionService.expireLocks();

    assertThat(promotionService.locks()).anySatisfy(lock -> assertThat(lock.status()).isEqualTo("EXPIRED"));
    assertThat(promotionService.wallet(20_201L)).singleElement().extracting("status").isEqualTo("AVAILABLE");
  }

  @Test
  void oneHundredUsersCanOnlySettleTenClaims() {
    VoucherView voucher = newVoucher(10);
    ExecutorService executor = Executors.newFixedThreadPool(16);
    try {
      List<CompletableFuture<ClaimSettlementResult>> futures = new ArrayList<>();
      for (long userId = 10_000; userId < 10_100; userId++) {
        long currentUser = userId;
        futures.add(CompletableFuture.supplyAsync(
            () -> settlementService.settle(SeckillClaimCommand.of(voucher.voucherId(), currentUser)), executor));
      }
      List<ClaimSettlementResult> results = futures.stream().map(CompletableFuture::join).toList();

      assertThat(results).filteredOn(result -> "CLAIMED".equals(result.status())).hasSize(10);
      assertThat(results).filteredOn(result -> "SOLD_OUT".equals(result.status())).hasSize(90);
      assertThat(promotionMapper.findVoucher(voucher.voucherId()).getStock()).isZero();
      assertThat(promotionMapper.countVoucherClaimsByStatus(voucher.voucherId(), "CLAIMED")).isEqualTo(10);
      assertThat(promotionMapper.countVoucherClaimsByStatus(voucher.voucherId(), "SOLD_OUT")).isEqualTo(90);
      assertThat(promotionMapper.countUserVouchersByVoucher(voucher.voucherId())).isEqualTo(10);
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void concurrentDuplicateMessagesCreateOneVoucherForOneUser() {
    VoucherView voucher = newVoucher(10);
    SeckillClaimCommand command = SeckillClaimCommand.of(voucher.voucherId(), 20_000L);
    ExecutorService executor = Executors.newFixedThreadPool(10);
    try {
      List<CompletableFuture<ClaimSettlementResult>> futures = new ArrayList<>();
      for (int attempt = 0; attempt < 20; attempt++) {
        futures.add(CompletableFuture.supplyAsync(() -> settlementService.settle(command), executor));
      }
      assertThat(futures.stream().map(CompletableFuture::join).toList())
          .allSatisfy(result -> assertThat(result.status()).isEqualTo("CLAIMED"));
      assertThat(settlementService.settle(command).status()).isEqualTo("CLAIMED");
      assertThat(promotionMapper.countUserVoucher(20_000L, voucher.voucherId())).isEqualTo(1);
      assertThat(promotionMapper.findVoucher(voucher.voucherId()).getStock()).isEqualTo(9);
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void mysqlSoldOutIsRecordedForRedisCompensation() {
    VoucherView voucher = newVoucher(0);

    ClaimSettlementResult result = settlementService.settle(SeckillClaimCommand.of(voucher.voucherId(), 203L));

    assertThat(result.status()).isEqualTo("SOLD_OUT");
    assertThat(promotionMapper.countUserVoucher(203L, voucher.voucherId())).isZero();
  }

  @Test
  void consumerCompensatesRedisWhenMysqlHasNoStock() {
    VoucherView voucher = newVoucher(0);
    SeckillClaimCommand command = SeckillClaimCommand.of(voucher.voucherId(), 209L);

    ClaimSettlementResult result = consumer().consume(command);

    assertThat(result.status()).isEqualTo("SOLD_OUT");
    verify(seckillGuard).compensate(209L, voucher.voucherId());
  }

  @Test
  void repeatedMessageCleansPendingAfterFirstCleanupFailure() {
    VoucherView voucher = newVoucher(1);
    SeckillClaimCommand command = SeckillClaimCommand.of(voucher.voucherId(), 210L);
    doThrow(new IllegalStateException("redis unavailable")).doNothing()
        .when(seckillGuard).complete(210L, voucher.voucherId());

    assertThatThrownBy(() -> consumer().consume(command)).isInstanceOf(IllegalStateException.class);
    ClaimSettlementResult retried = consumer().consume(command);

    assertThat(retried.status()).isEqualTo("CLAIMED");
    assertThat(promotionMapper.findVoucher(voucher.voucherId()).getStock()).isZero();
    assertThat(promotionMapper.countUserVoucher(210L, voucher.voucherId())).isEqualTo(1);
    verify(seckillGuard, times(2)).complete(210L, voucher.voucherId());
  }

  @Test
  void repeatedSoldOutMessageCompletesCompensationAfterFirstCleanupFailure() {
    VoucherView voucher = newVoucher(0);
    SeckillClaimCommand command = SeckillClaimCommand.of(voucher.voucherId(), 212L);
    doThrow(new IllegalStateException("redis unavailable")).doNothing()
        .when(seckillGuard).compensate(212L, voucher.voucherId());

    assertThatThrownBy(() -> consumer().consume(command)).isInstanceOf(IllegalStateException.class);
    ClaimSettlementResult retried = consumer().consume(command);

    assertThat(retried.status()).isEqualTo("SOLD_OUT");
    assertThat(promotionMapper.countUserVoucher(212L, voucher.voucherId())).isZero();
    verify(seckillGuard, times(2)).compensate(212L, voucher.voucherId());
  }

  @Test
  void failedFirstPublishKeepsAndDelaysPendingReservation() {
    when(seckillGuard.tryClaim(anyLong(), anyLong(), anyLong()))
        .thenReturn(VoucherSeckillGuard.ClaimResult.ACCEPTED);
    doThrow(new IllegalStateException("broker unavailable")).when(claimPublisher).publish(any());

    SeckillVoucherResponse response = promotionService.seckill(204L, 1000L, "first-send-fails");

    assertThat(response.status()).isEqualTo("PENDING");
    verify(seckillGuard).delayPending(org.mockito.ArgumentMatchers.eq(204L),
        org.mockito.ArgumentMatchers.eq(1000L), anyLong());
    verify(seckillGuard, never()).compensate(anyLong(), anyLong());
  }

  @Test
  void validatesActivityWindowBeforeRedis() {
    VoucherView future = newVoucher(2, LocalDateTime.now().plusHours(1), LocalDateTime.now().plusHours(2));
    VoucherView ended = newVoucher(2, LocalDateTime.now().minusHours(2), LocalDateTime.now().minusHours(1));

    assertThat(promotionService.seckill(205L, future.voucherId(), "future").status()).isEqualTo("NOT_STARTED");
    assertThat(promotionService.seckill(206L, ended.voucherId(), "ended").status()).isEqualTo("FAILED");
    verify(seckillGuard, never()).tryClaim(anyLong(), anyLong(), anyLong());
  }

  @Test
  void returnsRecoveringWhenStockMissingAndVoucherHasPendingReservations() {
    when(seckillGuard.tryClaim(anyLong(), anyLong(), anyLong()))
        .thenReturn(VoucherSeckillGuard.ClaimResult.STOCK_MISSING);
    when(seckillGuard.pendingCount(1000L)).thenReturn(1L);

    assertThat(promotionService.seckill(207L, 1000L, "missing-key").status()).isEqualTo("STOCK_RECOVERING");
    verify(seckillGuard, never()).syncStockIfAbsent(anyLong(), any(Integer.class));
  }

  @Test
  void retriesClaimAfterSafelyRestoringMissingRedisStock() {
    when(seckillGuard.tryClaim(anyLong(), anyLong(), anyLong()))
        .thenReturn(VoucherSeckillGuard.ClaimResult.STOCK_MISSING, VoucherSeckillGuard.ClaimResult.ACCEPTED);
    when(seckillGuard.pendingCount(1000L)).thenReturn(0L);

    assertThat(promotionService.seckill(208L, 1000L, "restore-and-claim").status()).isEqualTo("PENDING");
    verify(seckillGuard).syncStockIfAbsent(1000L, 100);
    verify(claimPublisher).publish(SeckillClaimCommand.of(1000L, 208L));
  }

  @Test
  void failsClosedWhenStateMarkerIsMissingEvenWithoutPending() {
    when(seckillGuard.isStateInitialized()).thenReturn(false);

    assertThat(promotionService.seckill(213L, 1000L, "state-lost").status()).isEqualTo("STOCK_RECOVERING");
    verify(seckillGuard, never()).tryClaim(anyLong(), anyLong(), anyLong());
    verify(seckillGuard, never()).pendingCount(anyLong());
    verify(seckillGuard, never()).syncStockIfAbsent(anyLong(), any(Integer.class));
  }

  @Test
  void failsClosedWhenMarkerDisappearsAfterLuaReportsMissingStock() {
    when(seckillGuard.isStateInitialized()).thenReturn(true, false);
    when(seckillGuard.tryClaim(anyLong(), anyLong(), anyLong()))
        .thenReturn(VoucherSeckillGuard.ClaimResult.STOCK_MISSING);

    assertThat(promotionService.seckill(214L, 1000L, "marker-lost-after-lua").status())
        .isEqualTo("STOCK_RECOVERING");
    verify(seckillGuard, never()).pendingCount(anyLong());
    verify(seckillGuard, never()).syncStockIfAbsent(anyLong(), any(Integer.class));
  }

  @Test
  void concurrentRecoveryCallersOnlyUseSetNxAndNeverOverwriteStock() {
    when(seckillGuard.tryClaim(anyLong(), anyLong(), anyLong())).thenReturn(
        VoucherSeckillGuard.ClaimResult.STOCK_MISSING, VoucherSeckillGuard.ClaimResult.ACCEPTED,
        VoucherSeckillGuard.ClaimResult.STOCK_MISSING, VoucherSeckillGuard.ClaimResult.ACCEPTED);
    when(seckillGuard.pendingCount(1000L)).thenReturn(0L);
    when(seckillGuard.syncStockIfAbsent(1000L, 100)).thenReturn(true, false);

    assertThat(promotionService.seckill(215L, 1000L, "recover-a").status()).isEqualTo("PENDING");
    assertThat(promotionService.seckill(216L, 1000L, "recover-b").status()).isEqualTo("PENDING");

    verify(seckillGuard, times(2)).syncStockIfAbsent(1000L, 100);
    verify(seckillGuard, never()).syncStock(1000L, 100);
  }

  @Test
  void stockCanOnlyChangeBeforeStartAndBeforeClaims() {
    LocalDateTime start = LocalDateTime.now().plusHours(2);
    VoucherView voucher = newVoucher(4, start, start.plusHours(1));
    VoucherAdminRequest legal = request(5, start, start.plusHours(1));

    assertThat(promotionService.updateVoucher(voucher.voucherId(), legal).stock()).isEqualTo(5);
    settlementService.settle(SeckillClaimCommand.of(voucher.voucherId(), 208L));

    assertThatThrownBy(() -> promotionService.updateVoucher(voucher.voucherId(), request(6, start, start.plusHours(1))))
        .isInstanceOf(BizException.class)
        .hasMessageContaining("不能直接修改库存");
  }

  @Test
  void duePendingOnlyRepublishesTheSameBusinessEvent() {
    VoucherView voucher = newVoucher(1);
    when(seckillGuard.findDuePending(org.mockito.ArgumentMatchers.eq(voucher.voucherId()), anyLong(),
        any(Integer.class)))
        .thenReturn(Set.of(211L));

    int recovered = recoveryScheduler.recoverPending();

    assertThat(recovered).isEqualTo(1);
    verify(claimPublisher).publish(SeckillClaimCommand.of(voucher.voucherId(), 211L));
    assertThat(promotionMapper.findClaimRetry("seckill:" + voucher.voucherId() + ":211").getStatus())
        .isEqualTo("RECOVERED");
    assertThat(promotionMapper.countUserVoucher(211L, voucher.voucherId())).isZero();
  }

  private SeckillClaimRocketMqConsumer consumer() {
    return new SeckillClaimRocketMqConsumer(objectMapper, settlementService, seckillGuard, recoveryScheduler,
        "localhost:9876", "test-seckill-consumer", "test-seckill-topic", 5);
  }

  private VoucherView newVoucher(int stock) {
    return newVoucher(stock, LocalDateTime.now().minusMinutes(1), LocalDateTime.now().plusHours(1));
  }

  private VoucherView newVoucher(int stock, LocalDateTime start, LocalDateTime end) {
    return promotionService.createVoucher(request(stock, start, end));
  }

  private VoucherAdminRequest request(int stock, LocalDateTime start, LocalDateTime end) {
    return new VoucherAdminRequest("测试秒杀券", "SECKILL", 300, stock, "ACTIVE", start, end);
  }
}
