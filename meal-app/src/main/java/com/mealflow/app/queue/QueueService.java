package com.mealflow.app.queue;

import com.mealflow.common.api.ErrorCode;
import com.mealflow.common.exception.BizException;
import com.mealflow.common.status.CapacityTokenStatus;
import com.mealflow.common.status.QueueTicketStatus;
import com.mealflow.infra.id.IdGenerator;
import com.mealflow.infra.idempotent.IdempotentTemplate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class QueueService {
  private static final int AVG_PREPARE_SECONDS = 180;
  private final IdGenerator idGenerator;
  private final IdempotentTemplate idempotentTemplate;
  private final ApplicationEventPublisher eventPublisher;
  private final Map<Long, QueueTicket> tickets = new ConcurrentHashMap<>();
  private final Map<Long, CapacityToken> tokens = new ConcurrentHashMap<>();
  private final Map<Long, PriorityQueue<WaitingTicket>> waitingQueues = new ConcurrentHashMap<>();
  private final Map<Long, Integer> merchantLimits = new ConcurrentHashMap<>();

  public QueueService(IdGenerator idGenerator, IdempotentTemplate idempotentTemplate,
      ApplicationEventPublisher eventPublisher) {
    this.idGenerator = idGenerator;
    this.idempotentTemplate = idempotentTemplate;
    this.eventPublisher = eventPublisher;
    merchantLimits.put(10L, 1);
  }

  public QueueApplyResponse apply(QueueApplyCommand command) {
    return idempotentTemplate.execute("queue-apply:" + command.userId() + ":" + command.requestId(), () -> {
      synchronized (this) {
        if (heldCount(command.merchantId()) < limit(command.merchantId())) {
          CapacityToken token = createToken(command.requestId(), command.merchantId(), null, command.expireTime());
          return QueueApplyResponse.ready(token.id);
        }
        long ticketId = idGenerator.next("queueTicket");
        String ticketNo = "QT" + ticketId;
        long score = System.currentTimeMillis() - Math.min(command.priorityWeightMillis(), 120_000);
        PriorityQueue<WaitingTicket> queue = waitingQueue(command.merchantId());
        int aheadCount = queue.size();
        int waitSeconds = estimateWaitSeconds(aheadCount, command.merchantId());
        QueueTicket ticket = new QueueTicket(
            ticketId,
            ticketNo,
            command.requestId(),
            command.userId(),
            command.merchantId(),
            QueueTicketStatus.WAITING,
            score,
            aheadCount,
            waitSeconds,
            command.expireTime(),
            command.snapshot()
        );
        tickets.put(ticketId, ticket);
        queue.add(new WaitingTicket(ticketId, ticketNo, score));
        return QueueApplyResponse.queued(ticketId, ticketNo, aheadCount, waitSeconds, command.expireTime());
      }
    });
  }

  public synchronized Optional<QueueReadyEvent> releaseCapacity(long capacityTokenId, String reason) {
    CapacityToken token = requireToken(capacityTokenId);
    if (token.status != CapacityTokenStatus.HELD) {
      return Optional.empty();
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
      QueueReadyEvent event = new QueueReadyEvent(ticket.id, ticket.ticketNo, nextToken.id);
      eventPublisher.publishEvent(event);
      return Optional.of(event);
    }
    return Optional.empty();
  }

  public synchronized void cancelTicket(long ticketId) {
    QueueTicket ticket = requireTicket(ticketId);
    if (ticket.status == QueueTicketStatus.WAITING) {
      ticket.status = QueueTicketStatus.CANCELLED;
      return;
    }
    if (ticket.status == QueueTicketStatus.READY || ticket.status == QueueTicketStatus.PROCESSING) {
      ticket.status = QueueTicketStatus.CANCELLED;
      tokens.values().stream()
          .filter(token -> token.ticketId != null && token.ticketId == ticketId)
          .findFirst()
          .ifPresent(token -> releaseCapacity(token.id, "TICKET_CANCELLED"));
      return;
    }
    throw new BizException(ErrorCode.ILLEGAL_STATUS, "当前排队状态不可取消");
  }

  public synchronized QueueTicket markProcessing(long ticketId) {
    QueueTicket ticket = requireTicket(ticketId);
    if (ticket.status == QueueTicketStatus.ORDER_CREATED) {
      return ticket;
    }
    if (ticket.status != QueueTicketStatus.READY && ticket.status != QueueTicketStatus.PROCESSING) {
      throw new BizException(ErrorCode.ILLEGAL_STATUS, "票据未就绪，不能创建订单");
    }
    ticket.status = QueueTicketStatus.PROCESSING;
    ticket.processingTime = LocalDateTime.now();
    return ticket;
  }

  public synchronized void confirmOrderCreated(long ticketId, long orderId) {
    QueueTicket ticket = requireTicket(ticketId);
    if (ticket.status == QueueTicketStatus.ORDER_CREATED && ticket.orderId != null && ticket.orderId == orderId) {
      return;
    }
    if (ticket.status != QueueTicketStatus.PROCESSING && ticket.status != QueueTicketStatus.READY) {
      throw new BizException(ErrorCode.ILLEGAL_STATUS, "票据状态不能回告订单");
    }
    ticket.status = QueueTicketStatus.ORDER_CREATED;
    ticket.orderId = orderId;
    tokens.values().stream()
        .filter(token -> token.ticketId != null && token.ticketId == ticketId)
        .forEach(token -> token.orderId = orderId);
  }

  public synchronized void bindTokenOrder(long capacityTokenId, long orderId) {
    CapacityToken token = requireToken(capacityTokenId);
    token.orderId = orderId;
  }

  public synchronized QueueTicketView getTicket(long ticketId) {
    QueueTicket ticket = requireTicket(ticketId);
    int aheadCount = ticket.status == QueueTicketStatus.WAITING ? aheadCount(ticket) : 0;
    return new QueueTicketView(
        ticket.id,
        ticket.ticketNo,
        ticket.status.name(),
        aheadCount,
        estimateWaitSeconds(aheadCount, ticket.merchantId),
        ticket.expireTime,
        ticket.status == QueueTicketStatus.WAITING || ticket.status == QueueTicketStatus.READY
    );
  }

  public QueueTicket requireTicket(long ticketId) {
    QueueTicket ticket = tickets.get(ticketId);
    if (ticket == null) {
      throw new BizException(ErrorCode.NOT_FOUND, "排队票据不存在");
    }
    return ticket;
  }

  public Optional<CapacityToken> findTokenByOrder(long orderId) {
    return tokens.values().stream()
        .filter(token -> token.orderId != null && token.orderId == orderId)
        .findFirst();
  }

  public synchronized void setMerchantLimit(long merchantId, int limit) {
    merchantLimits.put(merchantId, Math.max(1, limit));
  }

  public List<QueueTicketView> tickets() {
    return tickets.values().stream()
        .sorted(Comparator.comparingLong(ticket -> ticket.id))
        .map(ticket -> new QueueTicketView(
            ticket.id,
            ticket.ticketNo,
            ticket.status.name(),
            ticket.status == QueueTicketStatus.WAITING ? aheadCount(ticket) : 0,
            ticket.estimatedWaitSeconds,
            ticket.expireTime,
            ticket.status == QueueTicketStatus.WAITING || ticket.status == QueueTicketStatus.READY
        ))
        .toList();
  }

  public List<CapacityTokenView> tokens() {
    return tokens.values().stream()
        .sorted(Comparator.comparingLong(token -> token.id))
        .map(token -> new CapacityTokenView(token.id, token.merchantId, token.ticketId, token.orderId,
            token.status.name(), token.releaseReason))
        .toList();
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
    long tokenId = idGenerator.next("capacityToken");
    CapacityToken token = new CapacityToken(tokenId, requestId, merchantId, ticketId, null,
        CapacityTokenStatus.HELD, expireTime, null);
    tokens.put(tokenId, token);
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

  private CapacityToken requireToken(long tokenId) {
    CapacityToken token = tokens.get(tokenId);
    if (token == null) {
      throw new BizException(ErrorCode.NOT_FOUND, "产能令牌不存在");
    }
    return token;
  }

  private PriorityQueue<WaitingTicket> waitingQueue(long merchantId) {
    return waitingQueues.computeIfAbsent(merchantId, ignored -> new PriorityQueue<>(
        Comparator.comparingLong(WaitingTicket::score).thenComparing(WaitingTicket::ticketNo)
    ));
  }
}
