package com.mealflow.queue;

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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class QueueService {
  private static final int AVG_PREPARE_SECONDS = 180;
  private final IdGenerator idGenerator = new IdGenerator();
  private final IdempotentTemplate idempotentTemplate = new IdempotentTemplate();
  private final Map<Long, QueueTicket> tickets = new ConcurrentHashMap<>();
  private final Map<Long, CapacityToken> tokens = new ConcurrentHashMap<>();
  private final Map<Long, PriorityQueue<WaitingTicket>> waitingQueues = new ConcurrentHashMap<>();
  private final Map<Long, Integer> merchantLimits = new ConcurrentHashMap<>();

  public QueueService() {
    merchantLimits.put(10L, 1);
  }

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
            request.snapshot());
        tickets.put(ticketId, ticket);
        waitingQueue.add(new WaitingTicket(ticketId, ticketNo, score));
        return QueueApplyResponse.queued(ticketId, ticketNo, aheadCount, waitSeconds, request.expireTime());
      }
    });
  }

  public synchronized ReleaseCapacityResponse releaseCapacity(long capacityTokenId, String reason) {
    CapacityToken token = requireToken(capacityTokenId);
    if (token.status != CapacityTokenStatus.HELD) {
      return new ReleaseCapacityResponse(false, null);
    }
    token.status = CapacityTokenStatus.RELEASED;
    token.releaseReason = reason;

    PriorityQueue<WaitingTicket> queue = waitingQueue(token.merchantId);
    int scanned = 0;
    while (scanned < 50 && !queue.isEmpty()) {
      scanned++;
      WaitingTicket waiting = queue.poll();
      QueueTicket ticket = tickets.get(waiting.ticketId());
      if (ticket == null || ticket.status != QueueTicketStatus.WAITING || ticket.expireTime.isBefore(LocalDateTime.now())) {
        continue;
      }
      CapacityToken nextToken = createToken("ticket-ready:" + ticket.id, ticket.merchantId, ticket.id, ticket.expireTime);
      ticket.status = QueueTicketStatus.READY;
      ticket.readyTime = LocalDateTime.now();
      return new ReleaseCapacityResponse(true,
          new QueueReadyTicket(ticket.id, ticket.ticketNo, nextToken.id, ticket.snapshot));
    }
    return new ReleaseCapacityResponse(true, null);
  }

  public synchronized void bindTokenOrder(long capacityTokenId, long orderId) {
    CapacityToken token = requireToken(capacityTokenId);
    token.orderId = orderId;
  }

  public synchronized void confirmOrderCreated(long ticketId, long orderId) {
    QueueTicket ticket = requireTicket(ticketId);
    if (ticket.status == QueueTicketStatus.ORDER_CREATED && ticket.orderId != null && ticket.orderId == orderId) {
      return;
    }
    if (ticket.status != QueueTicketStatus.READY && ticket.status != QueueTicketStatus.PROCESSING) {
      throw new BizException(ErrorCode.ILLEGAL_STATUS, "票据状态不能回告订单");
    }
    ticket.status = QueueTicketStatus.ORDER_CREATED;
    ticket.orderId = orderId;
    tokens.values().stream()
        .filter(token -> token.ticketId != null && token.ticketId == ticketId)
        .forEach(token -> token.orderId = orderId);
  }

  public synchronized QueueTicketSnapshot markProcessing(long ticketId) {
    QueueTicket ticket = requireTicket(ticketId);
    if (ticket.status != QueueTicketStatus.READY && ticket.status != QueueTicketStatus.PROCESSING) {
      throw new BizException(ErrorCode.ILLEGAL_STATUS, "票据未就绪");
    }
    ticket.status = QueueTicketStatus.PROCESSING;
    ticket.processingTime = LocalDateTime.now();
    return ticket.snapshot;
  }

  public synchronized void cancelTicket(long ticketId) {
    QueueTicket ticket = requireTicket(ticketId);
    if (ticket.status == QueueTicketStatus.WAITING || ticket.status == QueueTicketStatus.READY) {
      ticket.status = QueueTicketStatus.CANCELLED;
      tokens.values().stream()
          .filter(token -> token.ticketId != null && token.ticketId == ticketId)
          .findFirst()
          .ifPresent(token -> releaseCapacity(token.id, "TICKET_CANCELLED"));
      return;
    }
    throw new BizException(ErrorCode.ILLEGAL_STATUS, "当前排队状态不可取消");
  }

  public synchronized QueueTicketView getTicket(long ticketId) {
    QueueTicket ticket = requireTicket(ticketId);
    int ahead = ticket.status == QueueTicketStatus.WAITING ? aheadCount(ticket) : 0;
    return new QueueTicketView(ticket.id, ticket.ticketNo, ticket.status.name(), ahead,
        estimateWaitSeconds(ahead, ticket.merchantId), ticket.expireTime,
        ticket.status == QueueTicketStatus.WAITING || ticket.status == QueueTicketStatus.READY);
  }

  public List<QueueTicketView> tickets() {
    return tickets.values().stream()
        .sorted(Comparator.comparingLong(ticket -> ticket.id))
        .map(ticket -> getTicket(ticket.id))
        .toList();
  }

  public List<CapacityTokenView> tokens() {
    return tokens.values().stream()
        .sorted(Comparator.comparingLong(token -> token.id))
        .map(token -> new CapacityTokenView(token.id, token.merchantId, token.ticketId, token.orderId,
            token.status.name(), token.releaseReason))
        .toList();
  }

  public Optional<CapacityToken> findTokenByOrder(long orderId) {
    return tokens.values().stream()
        .filter(token -> token.orderId != null && token.orderId == orderId)
        .findFirst();
  }

  public synchronized void setMerchantLimit(long merchantId, int limit) {
    merchantLimits.put(merchantId, Math.max(1, limit));
  }

  public Map<String, Object> metrics(long merchantId) {
    Map<String, Object> metrics = new HashMap<>();
    metrics.put("merchantId", merchantId);
    metrics.put("limit", limit(merchantId));
    metrics.put("held", heldCount(merchantId));
    metrics.put("waiting", waitingQueue(merchantId).size());
    return metrics;
  }

  private CapacityToken createToken(String requestId, long merchantId, Long ticketId, LocalDateTime expireTime) {
    long id = idGenerator.next("capacityToken");
    CapacityToken token = new CapacityToken(id, requestId, merchantId, ticketId, null, CapacityTokenStatus.HELD,
        expireTime, null);
    tokens.put(id, token);
    return token;
  }

  private QueueTicket requireTicket(long ticketId) {
    QueueTicket ticket = tickets.get(ticketId);
    if (ticket == null) {
      throw new BizException(ErrorCode.NOT_FOUND, "排队票据不存在");
    }
    return ticket;
  }

  private CapacityToken requireToken(long tokenId) {
    CapacityToken token = tokens.get(tokenId);
    if (token == null) {
      throw new BizException(ErrorCode.NOT_FOUND, "产能令牌不存在");
    }
    return token;
  }

  private int heldCount(long merchantId) {
    return (int) tokens.values().stream()
        .filter(token -> token.merchantId == merchantId && token.status == CapacityTokenStatus.HELD)
        .count();
  }

  private int limit(long merchantId) {
    return merchantLimits.getOrDefault(merchantId, 1);
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

  static class QueueTicket {
    final long id;
    final String ticketNo;
    final String requestId;
    final long userId;
    final long merchantId;
    QueueTicketStatus status;
    final long score;
    final int aheadCountSnapshot;
    final int estimatedWaitSeconds;
    final LocalDateTime expireTime;
    final QueueTicketSnapshot snapshot;
    Long orderId;
    LocalDateTime readyTime;
    LocalDateTime processingTime;

    QueueTicket(long id, String ticketNo, String requestId, long userId, long merchantId, QueueTicketStatus status,
        long score, int aheadCountSnapshot, int estimatedWaitSeconds, LocalDateTime expireTime,
        QueueTicketSnapshot snapshot) {
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
