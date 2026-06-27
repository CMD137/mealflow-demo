package com.mealflow.queue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mealflow.common.api.ErrorCode;
import com.mealflow.common.exception.BizException;
import com.mealflow.common.status.CapacityTokenStatus;
import com.mealflow.common.status.QueueTicketStatus;
import com.mealflow.infra.id.IdGenerator;
import com.mealflow.infra.idempotent.IdempotentTemplate;
import com.mealflow.queue.api.CapacityTokenView;
import com.mealflow.queue.api.QueueApplyRequest;
import com.mealflow.queue.api.QueueApplyResponse;
import com.mealflow.queue.api.QueueReadyTicket;
import com.mealflow.queue.api.QueueTicketSnapshot;
import com.mealflow.queue.api.QueueTicketView;
import com.mealflow.queue.api.ReleaseCapacityResponse;
import com.mealflow.queue.mapper.CapacityTokenRow;
import com.mealflow.queue.mapper.QueueMapper;
import com.mealflow.queue.mapper.QueueTicketRow;
import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QueueService {
  private static final int AVG_PREPARE_SECONDS = 180;

  private final IdGenerator idGenerator = new IdGenerator();
  private final IdempotentTemplate idempotentTemplate = new IdempotentTemplate();
  private final Map<Long, PriorityQueue<WaitingTicket>> waitingQueues = new ConcurrentHashMap<>();
  private final QueueMapper queueMapper;
  private final ObjectMapper objectMapper;

  public QueueService(QueueMapper queueMapper, ObjectMapper objectMapper) {
    this.queueMapper = queueMapper;
    this.objectMapper = objectMapper;
  }

  @PostConstruct
  void rebuildWaitingQueues() {
    queueMapper.findWaitingTickets(QueueTicketStatus.WAITING.name(), LocalDateTime.now())
        .forEach(ticket -> waitingQueue(ticket.getMerchantId())
            .add(new WaitingTicket(ticket.getId(), ticket.getTicketNo(), ticket.getScore())));
  }

  @Transactional
  public QueueApplyResponse apply(QueueApplyRequest request) {
    return idempotentTemplate.execute("queue:apply:" + request.userId() + ":" + request.requestId(), () -> {
      synchronized (this) {
        if (heldCount(request.merchantId()) < limit(request.merchantId())) {
          CapacityToken token = createToken(request.requestId(), request.merchantId(), null, request.expireTime());
          return QueueApplyResponse.ready(token.id);
        }

        long ticketId = idGenerator.next("queueTicket");
        String ticketNo = "QT" + ticketId;
        long score = System.currentTimeMillis() - Math.min(request.priorityWeightMillis(), 120_000);
        PriorityQueue<WaitingTicket> waitingQueue = waitingQueue(request.merchantId());
        int aheadCount = waitingQueue.size();
        int waitSeconds = estimateWaitSeconds(aheadCount, request.merchantId());
        QueueTicket ticket = new QueueTicket(ticketId, ticketNo, request.requestId(), request.userId(),
            request.merchantId(), QueueTicketStatus.WAITING, score, aheadCount, waitSeconds, request.expireTime(),
            request.snapshot(), null, null, null);
        insertTicket(ticket);
        waitingQueue.add(new WaitingTicket(ticketId, ticketNo, score));
        return QueueApplyResponse.queued(ticketId, ticketNo, aheadCount, waitSeconds, request.expireTime());
      }
    });
  }

  @Transactional
  public synchronized ReleaseCapacityResponse releaseCapacity(long capacityTokenId, String reason) {
    CapacityToken token = requireToken(capacityTokenId);
    if (token.status != CapacityTokenStatus.HELD) {
      return new ReleaseCapacityResponse(false, null);
    }
    updateTokenStatus(capacityTokenId, CapacityTokenStatus.RELEASED, reason);

    PriorityQueue<WaitingTicket> queue = waitingQueue(token.merchantId);
    int scanned = 0;
    while (scanned < 50 && !queue.isEmpty()) {
      scanned++;
      WaitingTicket waiting = queue.poll();
      Optional<QueueTicket> maybeTicket = findTicket(waiting.ticketId());
      if (maybeTicket.isEmpty()) {
        continue;
      }
      QueueTicket ticket = maybeTicket.get();
      if (ticket.status != QueueTicketStatus.WAITING) {
        continue;
      }
      if (ticket.expireTime.isBefore(LocalDateTime.now())) {
        updateTicketStatus(ticket.id, QueueTicketStatus.TIMEOUT, null, null, null);
        continue;
      }
      CapacityToken nextToken = createToken("ticket-ready:" + ticket.id, ticket.merchantId, ticket.id,
          ticket.expireTime);
      LocalDateTime readyTime = LocalDateTime.now();
      updateTicketStatus(ticket.id, QueueTicketStatus.READY, null, readyTime, null);
      return new ReleaseCapacityResponse(true,
          new QueueReadyTicket(ticket.id, ticket.ticketNo, nextToken.id, ticket.snapshot));
    }
    return new ReleaseCapacityResponse(true, null);
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
      findTokenByTicket(ticketId).ifPresent(token -> releaseCapacity(token.id, "TICKET_CANCELLED"));
      return;
    }
    throw new BizException(ErrorCode.ILLEGAL_STATUS, "ticket cannot be cancelled");
  }

  public synchronized QueueTicketView getTicket(long ticketId) {
    QueueTicket ticket = requireTicket(ticketId);
    int ahead = ticket.status == QueueTicketStatus.WAITING ? aheadCount(ticket) : 0;
    return new QueueTicketView(ticket.id, ticket.ticketNo, ticket.status.name(), ahead,
        estimateWaitSeconds(ahead, ticket.merchantId), ticket.expireTime,
        ticket.status == QueueTicketStatus.WAITING || ticket.status == QueueTicketStatus.READY);
  }

  public List<QueueTicketView> tickets() {
    return queueMapper.findTicketIds().stream().map(this::getTicket).toList();
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

  public Map<String, Object> metrics(long merchantId) {
    Map<String, Object> metrics = new HashMap<>();
    metrics.put("merchantId", merchantId);
    metrics.put("limit", limit(merchantId));
    metrics.put("held", heldCount(merchantId));
    metrics.put("waiting", waitingQueue(merchantId).size());
    return metrics;
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
    return new CapacityToken(id, requestId, merchantId, ticketId, null, CapacityTokenStatus.HELD, expireTime, null);
  }

  private void updateTokenStatus(long tokenId, CapacityTokenStatus status, String reason) {
    queueMapper.updateTokenStatus(tokenId, status.name(), reason, LocalDateTime.now());
  }

  private void updateTicketStatus(long ticketId, QueueTicketStatus status, Long orderId, LocalDateTime readyTime,
      LocalDateTime processingTime) {
    queueMapper.updateTicketStatus(ticketId, status.name(), orderId, readyTime, processingTime, LocalDateTime.now());
  }

  private QueueTicket requireTicket(long ticketId) {
    return findTicket(ticketId)
        .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "queue ticket not found"));
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
    return queueMapper.countHeldTokens(merchantId, CapacityTokenStatus.HELD.name());
  }

  private int limit(long merchantId) {
    Integer limit = queueMapper.findMerchantLimit(merchantId);
    return limit == null ? 1 : Math.max(1, limit);
  }

  private int estimateWaitSeconds(int aheadCount, long merchantId) {
    return (int) Math.ceil((double) aheadCount / limit(merchantId)) * AVG_PREPARE_SECONDS;
  }

  private int aheadCount(QueueTicket ticket) {
    List<WaitingTicket> waiting = new ArrayList<>(waitingQueue(ticket.merchantId));
    waiting.sort(Comparator.comparingLong(WaitingTicket::score).thenComparing(WaitingTicket::ticketNo));
    int index = 0;
    for (WaitingTicket candidate : waiting) {
      if (candidate.ticketId() == ticket.id) {
        return index;
      }
      index++;
    }
    return ticket.aheadCountSnapshot;
  }

  private PriorityQueue<WaitingTicket> waitingQueue(long merchantId) {
    return waitingQueues.computeIfAbsent(merchantId, ignored -> new PriorityQueue<>(
        Comparator.comparingLong(WaitingTicket::score).thenComparing(WaitingTicket::ticketNo)));
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
        row.getReleaseReason());
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

    CapacityToken(long id, String requestId, long merchantId, Long ticketId, Long orderId,
        CapacityTokenStatus status, LocalDateTime expireTime, String releaseReason) {
      this.id = id;
      this.requestId = requestId;
      this.merchantId = merchantId;
      this.ticketId = ticketId;
      this.orderId = orderId;
      this.status = status;
      this.expireTime = expireTime;
      this.releaseReason = releaseReason;
    }
  }

  record WaitingTicket(long ticketId, String ticketNo, long score) {
  }
}
