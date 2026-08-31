package com.mealflow.queue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mealflow.common.api.ErrorCode;
import com.mealflow.common.exception.BizException;
import com.mealflow.common.status.CapacityTokenStatus;
import com.mealflow.common.status.QueueTicketStatus;
import com.mealflow.infra.idempotent.IdempotentTemplate;
import com.mealflow.queue.api.CapacityTokenView;
import com.mealflow.queue.api.QueueApplyRequest;
import com.mealflow.queue.api.QueueApplyResponse;
import com.mealflow.queue.api.QueueReadyTicket;
import com.mealflow.queue.api.RecoverableQueueTicket;
import com.mealflow.queue.api.QueueTicketSnapshot;
import com.mealflow.queue.api.QueueTicketView;
import com.mealflow.queue.api.ReleaseCapacityResponse;
import com.mealflow.queue.capacity.CapacityInflightCounter;
import com.mealflow.queue.mapper.CapacityTokenRow;
import com.mealflow.queue.mapper.MerchantHeldCountRow;
import com.mealflow.queue.mapper.QueueMapper;
import com.mealflow.queue.mapper.QueueTicketRow;
import com.mealflow.queue.waiting.WaitingQueueStore;
import com.mealflow.queue.waiting.WaitingTicketEntry;
import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class QueueService {
  private static final int AVG_PREPARE_SECONDS = 180;

  private final QueueDatabaseIdGenerator idGenerator;
  private final IdempotentTemplate idempotentTemplate = new IdempotentTemplate();
  private final WaitingQueueStore waitingQueueStore;
  private final CapacityInflightCounter capacityInflightCounter;
  private final QueueMapper queueMapper;
  private final ObjectMapper objectMapper;
  private final boolean capacityInflightReconcileEnabled;

  public QueueService(QueueMapper queueMapper, ObjectMapper objectMapper, QueueDatabaseIdGenerator idGenerator,
      WaitingQueueStore waitingQueueStore,
      CapacityInflightCounter capacityInflightCounter,
      @Value("${mealflow.queue.inflight-reconcile.enabled:false}") boolean capacityInflightReconcileEnabled) {
    this.queueMapper = queueMapper;
    this.objectMapper = objectMapper;
    this.idGenerator = idGenerator;
    this.waitingQueueStore = waitingQueueStore;
    this.capacityInflightCounter = capacityInflightCounter;
    this.capacityInflightReconcileEnabled = capacityInflightReconcileEnabled;
  }

  @PostConstruct
  void rebuildRuntimeIndexes() {
    waitingQueueStore.rebuild(queueMapper.findWaitingTickets(QueueTicketStatus.WAITING.name(), LocalDateTime.now())
        .stream()
        .map(ticket -> new WaitingTicketEntry(ticket.getMerchantId(), ticket.getId(), ticket.getTicketNo(),
            ticket.getScore()))
        .toList());
    rebuildCapacityInflightCounter();
  }

  @Scheduled(initialDelayString = "${mealflow.queue.inflight-reconcile.initial-delay-ms:30000}",
      fixedDelayString = "${mealflow.queue.inflight-reconcile.fixed-delay-ms:30000}")
  void reconcileCapacityInflightCounter() {
    if (capacityInflightReconcileEnabled) {
      rebuildCapacityInflightCounter();
    }
  }

  /** Releases abandoned queue resources. Order-bound tokens are released by the order timeout saga. */
  @Scheduled(initialDelayString = "${mealflow.queue.expire.initial-delay-ms:30000}",
      fixedDelayString = "${mealflow.queue.expire.fixed-delay-ms:30000}")
  @Transactional
  public synchronized void expireStaleResources() {
    LocalDateTime now = LocalDateTime.now();
    for (QueueTicketRow row : queueMapper.findExpiredTickets(now)) {
      QueueTicket ticket = mapTicket(row);
      if (!timeoutTicket(ticket, now)) {
        continue;
      }
      if (ticket.status == QueueTicketStatus.WAITING) {
        waitingQueueStore.remove(ticket.merchantId,
            new WaitingTicketEntry(ticket.merchantId, ticket.id, ticket.ticketNo, ticket.score));
      }
      findTokenByTicket(ticket.id).ifPresent(token -> releaseCapacity(token.id, "TICKET_TIMEOUT"));
    }
    for (CapacityTokenRow row : queueMapper.findExpiredUnboundTokens(now, CapacityTokenStatus.HELD.name())) {
      releaseCapacity(row.getId(), "CAPACITY_TIMEOUT");
    }
  }

  @Transactional
  public QueueApplyResponse apply(QueueApplyRequest request) {
    QueueApplyResponse recovered = recoverApply(request);
    if (recovered != null) {
      return recovered;
    }
    return idempotentTemplate.execute("queue:apply:" + request.userId() + ":" + request.requestId(), () -> {
      {
        LocalDateTime now = LocalDateTime.now();
        queueMapper.ensureMerchantLimit(request.merchantId(), now);
        queueMapper.updateMerchantLimit(request.merchantId(), Math.max(1, request.effectiveCapacity()), now);
        if (queueMapper.tryAcquireCapacity(request.merchantId()) == 1) {
          CapacityToken token = createToken(request.requestId(), request.merchantId(), null, request.expireTime());
          return QueueApplyResponse.ready(token.id);
        }

        long ticketId = idGenerator.next("queueTicket");
        String ticketNo = "QT" + ticketId;
        long score = System.currentTimeMillis() - Math.min(request.priorityWeightMillis(), 120_000);
        int aheadCount = waitingQueueStore.size(request.merchantId());
        int waitSeconds = estimateWaitSeconds(aheadCount, request.merchantId());
        QueueTicket ticket = new QueueTicket(ticketId, ticketNo, request.requestId(), request.userId(),
            request.merchantId(), QueueTicketStatus.WAITING, score, aheadCount, waitSeconds, request.expireTime(),
            request.snapshot(), null, null, null);
        insertTicket(ticket);
        waitingQueueStore.add(request.merchantId(), new WaitingTicketEntry(request.merchantId(), ticketId, ticketNo,
            score));
        return QueueApplyResponse.queued(ticketId, ticketNo, aheadCount, waitSeconds, request.expireTime());
      }
    });
  }

  @Transactional
  public synchronized ReleaseCapacityResponse releaseCapacity(long capacityTokenId, String reason) {
    CapacityToken token = requireToken(capacityTokenId);
    if (token.status != CapacityTokenStatus.HELD) {
      return previousReleaseResult(token);
    }
    if (!releaseHeldToken(capacityTokenId, reason, token.merchantId)) {
      return new ReleaseCapacityResponse(false, null);
    }

    int scanned = 0;
    Optional<WaitingTicketEntry> maybeWaiting = waitingQueueStore.poll(token.merchantId);
    while (scanned < 50 && maybeWaiting.isPresent()) {
      scanned++;
      WaitingTicketEntry waiting = maybeWaiting.get();
      Optional<QueueTicket> maybeTicket = findTicket(waiting.ticketId());
      if (maybeTicket.isEmpty()) {
        maybeWaiting = waitingQueueStore.poll(token.merchantId);
        continue;
      }
      QueueTicket ticket = maybeTicket.get();
      if (ticket.status != QueueTicketStatus.WAITING) {
        maybeWaiting = waitingQueueStore.poll(token.merchantId);
        continue;
      }
      if (ticket.expireTime.isBefore(LocalDateTime.now())) {
        timeoutTicket(ticket, LocalDateTime.now());
        maybeWaiting = waitingQueueStore.poll(token.merchantId);
        continue;
      }
      if (queueMapper.tryAcquireCapacity(ticket.merchantId) != 1) {
        waitingQueueStore.add(ticket.merchantId,
            new WaitingTicketEntry(ticket.merchantId, ticket.id, ticket.ticketNo, ticket.score));
        break;
      }
      CapacityToken nextToken = createToken("ticket-ready:" + ticket.id, ticket.merchantId, ticket.id,
          ticket.expireTime);
      LocalDateTime readyTime = LocalDateTime.now();
      updateTicketStatus(ticket.id, QueueTicketStatus.READY, null, readyTime, null);
      queueMapper.recordReleaseResult(capacityTokenId, ticket.id, nextToken.id, LocalDateTime.now());
      return new ReleaseCapacityResponse(true,
          new QueueReadyTicket(ticket.id, ticket.ticketNo, nextToken.id, ticket.snapshot));
    }
    queueMapper.recordReleaseResult(capacityTokenId, null, null, LocalDateTime.now());
    return new ReleaseCapacityResponse(true, null);
  }

  private ReleaseCapacityResponse previousReleaseResult(CapacityToken token) {
    if (token.status != CapacityTokenStatus.RELEASED || token.releasedTicketId == null
        || token.releasedCapacityTokenId == null) {
      return new ReleaseCapacityResponse(false, null);
    }
    QueueTicket ticket = requireTicket(token.releasedTicketId);
    return new ReleaseCapacityResponse(false, new QueueReadyTicket(ticket.id, ticket.ticketNo,
        token.releasedCapacityTokenId, ticket.snapshot));
  }

  @Transactional
  public synchronized void bindTokenOrder(long capacityTokenId, long orderId) {
    requireToken(capacityTokenId);
    queueMapper.bindTokenOrder(capacityTokenId, orderId, LocalDateTime.now());
  }

  @Transactional
  public synchronized void confirmOrderCreated(long ticketId, long orderId) {
    QueueTicket ticket = requireTicket(ticketId);
    if (ticket.status == QueueTicketStatus.ORDER_CREATED && Objects.equals(ticket.orderId, orderId)) {
      return;
    }
    if (ticket.status != QueueTicketStatus.READY && ticket.status != QueueTicketStatus.PROCESSING) {
      throw new BizException(ErrorCode.ILLEGAL_STATUS, "ticket status cannot create order");
    }
    updateTicketStatus(ticketId, QueueTicketStatus.ORDER_CREATED, orderId, ticket.readyTime, ticket.processingTime);
    queueMapper.bindTicketTokensOrder(ticketId, orderId, LocalDateTime.now());
  }

  @Transactional
  public synchronized QueueTicketSnapshot markProcessing(long ticketId) {
    QueueTicket ticket = requireTicket(ticketId);
    if (ticket.status != QueueTicketStatus.READY && ticket.status != QueueTicketStatus.PROCESSING) {
      throw new BizException(ErrorCode.ILLEGAL_STATUS, "ticket is not ready");
    }
    LocalDateTime processingTime = ticket.processingTime == null ? LocalDateTime.now() : ticket.processingTime;
    updateTicketStatus(ticketId, QueueTicketStatus.PROCESSING, ticket.orderId, ticket.readyTime, processingTime);
    return ticket.snapshot;
  }

  @Transactional
  public synchronized void cancelTicket(long ticketId) {
    QueueTicket ticket = requireTicket(ticketId);
    if (ticket.status == QueueTicketStatus.WAITING || ticket.status == QueueTicketStatus.READY) {
      updateTicketStatus(ticket.id, QueueTicketStatus.CANCELLED, ticket.orderId, ticket.readyTime,
          ticket.processingTime);
      if (ticket.status == QueueTicketStatus.WAITING) {
        waitingQueueStore.remove(ticket.merchantId,
            new WaitingTicketEntry(ticket.merchantId, ticket.id, ticket.ticketNo, ticket.score));
      }
      findTokenByTicket(ticketId).ifPresent(token -> releaseCapacity(token.id, "TICKET_CANCELLED"));
      return;
    }
    throw new BizException(ErrorCode.ILLEGAL_STATUS, "ticket cannot be cancelled");
  }

  @Transactional
  public synchronized void cancelTicket(long ticketId, long userId) {
    QueueTicket ticket = requireTicket(ticketId);
    requireTicketOwner(ticket, userId);
    cancelTicket(ticketId);
  }

  private QueueApplyResponse recoverApply(QueueApplyRequest request) {
    LocalDateTime now = LocalDateTime.now();
    QueueTicketRow ticketRow = queueMapper.findTicketByRequest(request.userId(), request.requestId(), now);
    if (ticketRow != null) {
      QueueTicket ticket = mapTicket(ticketRow);
      return QueueApplyResponse.queued(ticket.id, ticket.ticketNo, aheadCount(ticket),
          estimateWaitSeconds(aheadCount(ticket), ticket.merchantId), ticket.expireTime);
    }
    CapacityTokenRow tokenRow = queueMapper.findDirectTokenByRequest(request.requestId(), request.merchantId(), now);
    if (tokenRow != null) {
      return QueueApplyResponse.ready(tokenRow.getId());
    }
    return null;
  }

  public QueueTicketView activeTicket(long userId) {
    QueueTicketRow row = queueMapper.findActiveTicketByUser(userId, LocalDateTime.now());
    return row == null ? null : ticketView(mapTicket(row));
  }

  public List<QueueTicketView> ticketHistory(long userId, int limit) {
    return queueMapper.findRecentTicketsByUser(userId, Math.max(1, Math.min(limit, 50))).stream()
        .map(this::mapTicket)
        .map(this::ticketView)
        .toList();
  }

  public synchronized QueueTicketView getTicket(long ticketId) {
    QueueTicket ticket = requireTicket(ticketId);
    return ticketView(ticket);
  }

  public synchronized QueueTicketView getTicket(long ticketId, long userId) {
    QueueTicket ticket = requireTicket(ticketId);
    requireTicketOwner(ticket, userId);
    return ticketView(ticket);
  }

  public List<QueueTicketView> tickets() {
    return queueMapper.findTicketIds().stream().map(this::getTicket).toList();
  }

  public List<RecoverableQueueTicket> recoverableTickets(int limit) {
    return queueMapper.findRecoverableTickets(LocalDateTime.now(), Math.max(1, Math.min(limit, 100))).stream()
        .map(this::mapTicket)
        .map(ticket -> findTokenByTicket(ticket.id)
            .map(token -> new RecoverableQueueTicket(ticket.id, token.id))
            .orElse(null))
        .filter(Objects::nonNull)
        .toList();
  }

  public List<CapacityTokenView> tokens() {
    return queueMapper.findTokens().stream()
        .map(token -> new CapacityTokenView(token.getId(), token.getMerchantId(), token.getTicketId(),
            token.getOrderId(), token.getStatus(), token.getReleaseReason()))
        .toList();
  }

  public Optional<CapacityToken> findTokenByOrder(long orderId) {
    return Optional.ofNullable(queueMapper.findTokenByOrder(orderId)).map(this::mapToken);
  }

  @Transactional
  public synchronized void setMerchantLimit(long merchantId, int limit) {
    int normalizedLimit = Math.max(1, limit);
    int updated = queueMapper.updateMerchantLimit(merchantId, normalizedLimit, LocalDateTime.now());
    if (updated == 0) {
      queueMapper.insertMerchantLimit(merchantId, normalizedLimit, LocalDateTime.now());
    }
  }

  public synchronized void setMerchantLimit(long currentMerchantId, long merchantId, int limit) {
    requireMerchantOwnership(currentMerchantId, merchantId);
    setMerchantLimit(merchantId, limit);
  }

  public Map<String, Object> metrics(long merchantId) {
    Map<String, Object> metrics = new HashMap<>();
    metrics.put("merchantId", merchantId);
    metrics.put("limit", limit(merchantId));
    metrics.put("held", heldCount(merchantId));
    metrics.put("waiting", waitingQueueStore.size(merchantId));
    return metrics;
  }

  public Map<String, Object> metrics(long currentMerchantId, long merchantId) {
    requireMerchantOwnership(currentMerchantId, merchantId);
    return metrics(merchantId);
  }

  private void insertTicket(QueueTicket ticket) {
    queueMapper.insertTicket(ticket.id, ticket.ticketNo, ticket.requestId, ticket.userId, ticket.merchantId,
        ticket.status.name(), ticket.score, ticket.aheadCountSnapshot, ticket.estimatedWaitSeconds,
        ticket.expireTime, toJson(ticket.snapshot), ticket.orderId, ticket.readyTime, ticket.processingTime,
        LocalDateTime.now());
  }

  private CapacityToken createToken(String requestId, long merchantId, Long ticketId, LocalDateTime expireTime) {
    long id = idGenerator.next("capacityToken");
    queueMapper.insertToken(id, requestId, merchantId, ticketId, null, CapacityTokenStatus.HELD.name(), expireTime,
        null, LocalDateTime.now());
    afterCommitOrNow(() -> capacityInflightCounter.increment(merchantId));
    return new CapacityToken(id, requestId, merchantId, ticketId, null, CapacityTokenStatus.HELD, expireTime, null);
  }

  private boolean releaseHeldToken(long tokenId, String reason, long merchantId) {
    int updated = queueMapper.updateTokenStatusFromStatus(tokenId, CapacityTokenStatus.HELD.name(),
        CapacityTokenStatus.RELEASED.name(), reason, LocalDateTime.now());
    if (updated == 0) {
      return false;
    }
    queueMapper.releaseCapacity(merchantId);
    afterCommitOrNow(() -> capacityInflightCounter.decrement(merchantId));
    return true;
  }

  private void updateTicketStatus(long ticketId, QueueTicketStatus status, Long orderId, LocalDateTime readyTime,
      LocalDateTime processingTime) {
    queueMapper.updateTicketStatus(ticketId, status.name(), orderId, readyTime, processingTime, LocalDateTime.now());
  }

  private boolean timeoutTicket(QueueTicket ticket, LocalDateTime now) {
    if (queueMapper.expireTicket(ticket.id, ticket.status.name(), QueueTicketStatus.TIMEOUT.name(), now) != 1) {
      return false;
    }
    queueMapper.enqueueTimeoutNotification(ticket.id, ticket.userId, now);
    return true;
  }

  private QueueTicket requireTicket(long ticketId) {
    return findTicket(ticketId)
        .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "queue ticket not found"));
  }

  private void requireTicketOwner(QueueTicket ticket, long userId) {
    if (ticket.userId != userId) {
      throw new BizException(ErrorCode.FORBIDDEN, "queue ticket does not belong to current user");
    }
  }

  private void requireMerchantOwnership(long currentMerchantId, long merchantId) {
    if (currentMerchantId != merchantId) {
      throw new BizException(ErrorCode.FORBIDDEN, "merchant resource does not belong to current merchant");
    }
  }

  private QueueTicketView ticketView(QueueTicket ticket) {
    int ahead = ticket.status == QueueTicketStatus.WAITING ? aheadCount(ticket) : 0;
    return new QueueTicketView(ticket.id, ticket.ticketNo, ticket.status.name(), ahead,
        estimateWaitSeconds(ahead, ticket.merchantId), ticket.expireTime,
        ticket.status == QueueTicketStatus.WAITING || ticket.status == QueueTicketStatus.READY, ticket.orderId);
  }

  private Optional<QueueTicket> findTicket(long ticketId) {
    return Optional.ofNullable(queueMapper.findTicket(ticketId)).map(this::mapTicket);
  }

  private CapacityToken requireToken(long tokenId) {
    return findToken(tokenId)
        .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "capacity token not found"));
  }

  private Optional<CapacityToken> findToken(long tokenId) {
    return Optional.ofNullable(queueMapper.findToken(tokenId)).map(this::mapToken);
  }

  private Optional<CapacityToken> findTokenByTicket(long ticketId) {
    return Optional.ofNullable(queueMapper.findHeldTokenByTicket(ticketId, CapacityTokenStatus.HELD.name()))
        .map(this::mapToken);
  }

  private int heldCount(long merchantId) {
    return capacityInflightCounter.count(merchantId);
  }

  private int limit(long merchantId) {
    Integer limit = queueMapper.findMerchantLimit(merchantId);
    return limit == null ? 1 : Math.max(1, limit);
  }

  private int estimateWaitSeconds(int aheadCount, long merchantId) {
    return (int) Math.ceil((aheadCount + 1.0) / limit(merchantId)) * AVG_PREPARE_SECONDS;
  }

  private int aheadCount(QueueTicket ticket) {
    return waitingQueueStore.rank(ticket.merchantId,
        new WaitingTicketEntry(ticket.merchantId, ticket.id, ticket.ticketNo, ticket.score),
        ticket.aheadCountSnapshot);
  }

  private void rebuildCapacityInflightCounter() {
    Map<Long, Integer> heldCounts = new HashMap<>();
    for (MerchantHeldCountRow row : queueMapper.findHeldTokenCounts(CapacityTokenStatus.HELD.name())) {
      heldCounts.put(row.getMerchantId(), row.getHeldCount());
    }
    capacityInflightCounter.rebuild(heldCounts);
  }

  private void afterCommitOrNow(Runnable action) {
    if (TransactionSynchronizationManager.isSynchronizationActive()
        && TransactionSynchronizationManager.isActualTransactionActive()) {
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCommit() {
          action.run();
        }
      });
      return;
    }
    action.run();
  }

  private QueueTicket mapTicket(QueueTicketRow row) {
    return new QueueTicket(row.getId(), row.getTicketNo(), row.getRequestId(), row.getUserId(),
        row.getMerchantId(), QueueTicketStatus.valueOf(row.getStatus()), row.getScore(),
        row.getAheadCountSnapshot(), row.getEstimatedWaitSeconds(), row.getExpireTime(),
        fromJson(row.getSnapshotJson()), row.getOrderId(), row.getReadyTime(), row.getProcessingTime());
  }

  private CapacityToken mapToken(CapacityTokenRow row) {
    return new CapacityToken(row.getId(), row.getRequestId(), row.getMerchantId(), row.getTicketId(),
        row.getOrderId(), CapacityTokenStatus.valueOf(row.getStatus()), row.getExpireTime(),
        row.getReleaseReason(), row.getReleasedTicketId(), row.getReleasedCapacityTokenId());
  }

  private String toJson(QueueTicketSnapshot snapshot) {
    try {
      return objectMapper.writeValueAsString(snapshot);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("failed to serialize queue snapshot", e);
    }
  }

  private QueueTicketSnapshot fromJson(String json) {
    try {
      return objectMapper.readValue(json, QueueTicketSnapshot.class);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("failed to deserialize queue snapshot", e);
    }
  }

  static class QueueTicket {
    final long id;
    final String ticketNo;
    final String requestId;
    final long userId;
    final long merchantId;
    final QueueTicketStatus status;
    final long score;
    final int aheadCountSnapshot;
    final int estimatedWaitSeconds;
    final LocalDateTime expireTime;
    final QueueTicketSnapshot snapshot;
    final Long orderId;
    final LocalDateTime readyTime;
    final LocalDateTime processingTime;

    QueueTicket(long id, String ticketNo, String requestId, long userId, long merchantId, QueueTicketStatus status,
        long score, int aheadCountSnapshot, int estimatedWaitSeconds, LocalDateTime expireTime,
        QueueTicketSnapshot snapshot, Long orderId, LocalDateTime readyTime, LocalDateTime processingTime) {
      this.id = id;
      this.ticketNo = ticketNo;
      this.requestId = requestId;
      this.userId = userId;
      this.merchantId = merchantId;
      this.status = status;
      this.score = score;
      this.aheadCountSnapshot = aheadCountSnapshot;
      this.estimatedWaitSeconds = estimatedWaitSeconds;
      this.expireTime = expireTime;
      this.snapshot = snapshot;
      this.orderId = orderId;
      this.readyTime = readyTime;
      this.processingTime = processingTime;
    }
  }

  public static class CapacityToken {
    public final long id;
    final String requestId;
    final long merchantId;
    final Long ticketId;
    Long orderId;
    CapacityTokenStatus status;
    final LocalDateTime expireTime;
    String releaseReason;
    final Long releasedTicketId;
    final Long releasedCapacityTokenId;

    CapacityToken(long id, String requestId, long merchantId, Long ticketId, Long orderId,
        CapacityTokenStatus status, LocalDateTime expireTime, String releaseReason) {
      this(id, requestId, merchantId, ticketId, orderId, status, expireTime, releaseReason, null, null);
    }

    CapacityToken(long id, String requestId, long merchantId, Long ticketId, Long orderId,
        CapacityTokenStatus status, LocalDateTime expireTime, String releaseReason,
        Long releasedTicketId, Long releasedCapacityTokenId) {
      this.id = id;
      this.requestId = requestId;
      this.merchantId = merchantId;
      this.ticketId = ticketId;
      this.orderId = orderId;
      this.status = status;
      this.expireTime = expireTime;
      this.releaseReason = releaseReason;
      this.releasedTicketId = releasedTicketId;
      this.releasedCapacityTokenId = releasedCapacityTokenId;
    }
  }
}
