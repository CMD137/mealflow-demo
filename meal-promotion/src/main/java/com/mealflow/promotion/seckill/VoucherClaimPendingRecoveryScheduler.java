package com.mealflow.promotion.seckill;

import com.mealflow.promotion.mapper.PromotionMapper;
import com.mealflow.promotion.mapper.VoucherClaimRetryRow;
import com.mealflow.promotion.mq.SeckillClaimPublisher;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class VoucherClaimPendingRecoveryScheduler {
  private final PromotionMapper promotionMapper;
  private final VoucherSeckillGuard seckillGuard;
  private final SeckillClaimPublisher publisher;
  private final int batchSize;
  private final long maxBackoffMs;
  private final boolean enabled;
  private final int maxRetryAttempts;

  public VoucherClaimPendingRecoveryScheduler(PromotionMapper promotionMapper, VoucherSeckillGuard seckillGuard,
      SeckillClaimPublisher publisher,
      @Value("${mealflow.promotion.pending-recovery.enabled:true}") boolean enabled,
      @Value("${mealflow.promotion.pending-recovery.batch-size:100}") int batchSize,
      @Value("${mealflow.promotion.pending-recovery.max-backoff-ms:300000}") long maxBackoffMs,
      @Value("${mealflow.promotion.pending-recovery.max-retry-attempts:8}") int maxRetryAttempts) {
    this.promotionMapper = promotionMapper;
    this.seckillGuard = seckillGuard;
    this.publisher = publisher;
    this.enabled = enabled;
    this.batchSize = Math.max(1, batchSize);
    this.maxBackoffMs = Math.max(60_000, maxBackoffMs);
    this.maxRetryAttempts = Math.max(1, maxRetryAttempts);
  }

  @Scheduled(
      initialDelayString = "${mealflow.promotion.pending-recovery.initial-delay-ms:10000}",
      fixedDelayString = "${mealflow.promotion.pending-recovery.fixed-delay-ms:5000}"
  )
  public void scheduledRecover() {
    if (enabled) {
      recoverPending();
    }
  }

  public int recoverPending() {
    long now = System.currentTimeMillis();
    int recovered = 0;
    for (Long voucherId : promotionMapper.findVoucherIds()) {
      int remaining = batchSize - recovered;
      if (remaining <= 0) {
        break;
      }
      for (Long userId : seckillGuard.findDuePending(voucherId, now, remaining)) {
        recoverOne(SeckillClaimCommand.of(voucherId, userId), now);
        recovered++;
      }
    }
    return recovered;
  }

  public void markRecovered(String eventKey) {
    VoucherClaimRetryRow retry = promotionMapper.findClaimRetry(eventKey);
    if (retry == null) {
      return;
    }
    retry.setStatus("RECOVERED");
    retry.setLastError(null);
    promotionMapper.updateClaimRetry(retry);
  }

  private void recoverOne(SeckillClaimCommand command, long now) {
    VoucherClaimRetryRow retry = promotionMapper.findClaimRetry(command.eventKey());
    int retryCount = retry == null ? 1 : retry.getRetryCount() + 1;
    long nextRetryTime = now + backoffMs(retryCount);

    // Move the score first so overlapping scheduler runs cannot publish the same reservation at once.
    seckillGuard.delayPending(command.userId(), command.voucherId(), nextRetryTime);
    String status = "RECOVERED";
    String error = null;
    try {
      publisher.publish(command);
    } catch (RuntimeException ex) {
      error = trimError(ex);
      if (retryCount >= maxRetryAttempts) {
        try {
          // Do not declare a terminal failure until the original Redis
          // reservation has been released successfully. This keeps stock and
          // the per-user claim guard consistent when cleanup is unavailable.
          seckillGuard.compensate(command.userId(), command.voucherId());
          status = "DEAD";
          nextRetryTime = now;
        } catch (RuntimeException compensationFailure) {
          status = "RETRY";
          error = trimError(error + "; compensation failed: " + trimError(compensationFailure));
        }
      } else {
        status = "RETRY";
      }
    }
    saveRetry(retry, command, status, retryCount, error, nextRetryTime);
  }

  private void saveRetry(VoucherClaimRetryRow retry, SeckillClaimCommand command, String status, int retryCount,
      String error, long nextRetryTime) {
    VoucherClaimRetryRow target = retry == null ? new VoucherClaimRetryRow() : retry;
    target.setEventKey(command.eventKey());
    target.setUserId(command.userId());
    target.setVoucherId(command.voucherId());
    target.setStatus(status);
    target.setRetryCount(retryCount);
    target.setLastError(error);
    target.setNextRetryTime(LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(nextRetryTime),
        ZoneId.systemDefault()));
    if (retry == null) {
      promotionMapper.insertClaimRetry(target);
    } else {
      promotionMapper.updateClaimRetry(target);
    }
  }

  private long backoffMs(int retryCount) {
    if (retryCount <= 1) {
      return Math.min(10_000, maxBackoffMs);
    }
    if (retryCount == 2) {
      return Math.min(30_000, maxBackoffMs);
    }
    if (retryCount == 3) {
      return Math.min(60_000, maxBackoffMs);
    }
    int shift = Math.min(retryCount - 3, 20);
    return Math.min(60_000L << shift, maxBackoffMs);
  }

  private String trimError(RuntimeException ex) {
    String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
    return trimError(message);
  }

  private String trimError(String message) {
    return message.length() <= 512 ? message : message.substring(0, 512);
  }
}
