package com.mealflow.fulfillment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mealflow.common.api.Result;
import com.mealflow.common.status.LocalEventStatus;
import com.mealflow.fulfillment.api.FulfillmentOperationView;
import com.mealflow.fulfillment.api.LocalEventView;
import com.mealflow.fulfillment.api.OrderView;
import com.mealflow.fulfillment.config.ServiceEndpoints;
import com.mealflow.fulfillment.mapper.FulfillmentMapper;
import com.mealflow.fulfillment.mapper.FulfillmentOperationRow;
import com.mealflow.fulfillment.mapper.LocalEventMapper;
import com.mealflow.fulfillment.mapper.LocalEventRow;
import com.mealflow.fulfillment.mapper.MealReadyTaskMapper;
import com.mealflow.fulfillment.mapper.MealReadyTaskRow;
import com.mealflow.fulfillment.outbox.OutboxEventPublisher;
import com.mealflow.infra.event.EventKey;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestTemplate;

@Service
public class FulfillmentService {
  private static final Duration OUTBOX_SENDING_TIMEOUT = Duration.ofMinutes(1);

  private final FulfillmentIdGenerator idGenerator;
  private final RestTemplate restTemplate;
  private final ServiceEndpoints endpoints;
  private final FulfillmentMapper fulfillmentMapper;
  private final LocalEventMapper localEventMapper;
  private final OutboxEventPublisher outboxEventPublisher;
  private final ObjectMapper objectMapper;
  private final MealReadyTaskMapper mealReadyTaskMapper;
  private final TransactionTemplate transactionTemplate;

  public FulfillmentService(RestTemplate restTemplate, ServiceEndpoints endpoints, FulfillmentMapper fulfillmentMapper,
      LocalEventMapper localEventMapper, OutboxEventPublisher outboxEventPublisher, ObjectMapper objectMapper,
      FulfillmentIdGenerator idGenerator, MealReadyTaskMapper mealReadyTaskMapper,
      PlatformTransactionManager transactionManager) {
    this.restTemplate = restTemplate;
    this.endpoints = endpoints;
    this.fulfillmentMapper = fulfillmentMapper;
    this.localEventMapper = localEventMapper;
    this.outboxEventPublisher = outboxEventPublisher;
    this.objectMapper = objectMapper;
    this.idGenerator = idGenerator;
    this.mealReadyTaskMapper = mealReadyTaskMapper;
    this.transactionTemplate = new TransactionTemplate(transactionManager);
  }

  @Transactional
  public OrderView accept(long merchantId, long orderId, String requestId) {
    OrderView order = postOrder(merchantId, orderId, "merchant-accept");
    recordOperation(requestId, orderId, "ACCEPT", "SUCCESS", "merchant accepted");
    appendFulfillmentEvent("FulfillmentAccepted", requestId, order);
    return order;
  }

  public OrderView mealReady(long merchantId, long orderId, String requestId) {
    MealReadyTaskRow existing = mealReadyTaskMapper.findByRequestId(requestId);
    if (existing != null) {
      processMealReadyTask(existing);
      return fromJson(existing.getOrderJson(), OrderView.class);
    }
    OrderView order = postOrder(merchantId, orderId, "meal-ready");
    mealReadyTaskMapper.insert(requestId, orderId, order.capacityTokenId(), toJson(order), LocalDateTime.now());
    processMealReadyTask(mealReadyTaskMapper.findByRequestId(requestId));
    return order;
  }

  public int dispatchMealReadyTasks(int limit) {
    mealReadyTaskMapper.recoverExpired(LocalDateTime.now());
    int completed = 0;
    for (MealReadyTaskRow task : mealReadyTaskMapper.findReady(LocalDateTime.now(), limit)) {
      if (processMealReadyTask(task)) {
        completed++;
      }
    }
    return completed;
  }

  private boolean processMealReadyTask(MealReadyTaskRow task) {
    if ("SUCCESS".equals(task.getStatus())) {
      return true;
    }
    LocalDateTime now = LocalDateTime.now();
    if (mealReadyTaskMapper.markProcessing(task.getRequestId(), now, now.plusMinutes(1)) == 0) {
      return false;
    }
    try {
      if (!task.isReleaseDone()) {
        ReleaseCapacityResponse released = releaseCapacity(task.getCapacityTokenId(),
            "meal-ready-capacity:" + task.getRequestId(), "MEAL_READY");
        Long ticketId = released.readyTicket() == null ? null : released.readyTicket().ticketId();
        Long capacityTokenId = released.readyTicket() == null ? null : released.readyTicket().capacityTokenId();
        mealReadyTaskMapper.markReleased(task.getRequestId(), ticketId, capacityTokenId, LocalDateTime.now());
        task.setReleaseDone(true);
        task.setReadyTicketId(ticketId);
        task.setReadyCapacityTokenId(capacityTokenId);
      }
      if (task.getReadyTicketId() != null && !task.isPromoteDone()) {
        postTicketOrder(task.getReadyTicketId(), task.getReadyCapacityTokenId());
        mealReadyTaskMapper.markPromoted(task.getRequestId(), LocalDateTime.now());
        task.setPromoteDone(true);
      }
      completeMealReadyTask(task);
      return true;
    } catch (RuntimeException ex) {
      LocalDateTime failedAt = LocalDateTime.now();
      mealReadyTaskMapper.markFailed(task.getRequestId(), trimError(ex),
          retryAt(task.getRetryCount() + 1, failedAt), failedAt);
      return false;
    }
  }

  private void completeMealReadyTask(MealReadyTaskRow task) {
    transactionTemplate.executeWithoutResult(status -> {
      if (mealReadyTaskMapper.markSuccess(task.getRequestId(), LocalDateTime.now()) == 0) {
        return;
      }
      OrderView order = fromJson(task.getOrderJson(), OrderView.class);
      String message = task.getReadyTicketId() == null
          ? "capacity released" : "capacity released and ticket promoted";
      recordOperation(task.getRequestId(), task.getOrderId(), "MEAL_READY", "SUCCESS", message);
      appendFulfillmentEvent("FulfillmentMealReady", task.getRequestId(), order);
    });
  }

  private void postTicketOrder(long ticketId, long capacityTokenId) {
    Result<?> result = restTemplate.postForObject(endpoints.order() + "/orders/internal/from-ticket/" + ticketId + "/"
        + capacityTokenId, null, Result.class);
    if (result == null || !result.success()) {
      throw new IllegalStateException(result == null ? "ticket order call failed" : result.message());
    }
  }

  @Transactional
  public OrderView pickedUp(long merchantId, long orderId, String requestId) {
    OrderView order = postOrder(merchantId, orderId, "picked-up");
    recordOperation(requestId, orderId, "PICKED_UP", "SUCCESS", "delivery picked up");
    appendFulfillmentEvent("FulfillmentPickedUp", requestId, order);
    return order;
  }

  @Transactional
  public OrderView delivered(long merchantId, long orderId, String requestId) {
    OrderView order = postOrder(merchantId, orderId, "delivered");
    recordOperation(requestId, orderId, "DELIVERED", "SUCCESS", "delivery completed");
    appendFulfillmentEvent("FulfillmentDelivered", requestId, order);
    return order;
  }

  public List<FulfillmentOperationView> operations() {
    return fulfillmentMapper.findOperations().stream().map(this::mapOperation).toList();
  }

  public List<LocalEventView> events() {
    return localEventMapper.findAll().stream().map(this::eventView).toList();
  }

  public int dispatchPendingEvents(int limit) {
    recoverStaleSendingEvents();
    int sent = 0;
    for (LocalEventRow row : localEventMapper.findDispatchable(limit)) {
      if (localEventMapper.markSending(row.getId(), LocalDateTime.now()) == 0) {
        continue;
      }
      try {
        outboxEventPublisher.publish(eventView(row));
        localEventMapper.markSent(row.getId(), LocalDateTime.now());
        sent++;
      } catch (RuntimeException ex) {
        localEventMapper.markFailed(row.getId(), trimError(ex), LocalDateTime.now());
      }
    }
    return sent;
  }

  public int recoverStaleSendingEvents() {
    LocalDateTime now = LocalDateTime.now();
    return localEventMapper.markStaleSendingFailedBefore(now.minus(OUTBOX_SENDING_TIMEOUT), now);
  }

  private OrderView postOrder(long merchantId, long orderId, String action) {
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Merchant-Id", Long.toString(merchantId));
    Result<OrderView> result = restTemplate.exchange(
        endpoints.order() + "/orders/" + orderId + "/" + action,
        HttpMethod.POST,
        new HttpEntity<>(headers),
        new ParameterizedTypeReference<Result<OrderView>>() {
        }).getBody();
    if (result == null || !result.success()) {
      throw new IllegalStateException(result == null ? "order 调用失败" : result.message());
    }
    return result.data();
  }

  private ReleaseCapacityResponse releaseCapacity(long capacityTokenId, String requestId, String reason) {
    Result<ReleaseCapacityResponse> result = restTemplate.exchange(
        endpoints.queue() + "/queue/internal/capacity/" + capacityTokenId + "/release",
        HttpMethod.POST,
        new HttpEntity<>(new ReleaseCapacityRequest(requestId, reason)),
        new ParameterizedTypeReference<Result<ReleaseCapacityResponse>>() {
        }).getBody();
    if (result == null || !result.success()) {
      throw new IllegalStateException(result == null ? "queue 调用失败" : result.message());
    }
    return result.data();
  }

  private void recordOperation(String requestId, long orderId, String action, String status, String message) {
    fulfillmentMapper.insertOperation(idGenerator.next("fulfillmentOperation"), requestId, orderId, action, status,
        message, LocalDateTime.now());
  }

  private FulfillmentOperationView mapOperation(FulfillmentOperationRow row) {
    return new FulfillmentOperationView(row.getId(), row.getRequestId(), row.getOrderId(), row.getAction(),
        row.getStatus(), row.getMessage(), row.getCreateTime());
  }

  private void appendFulfillmentEvent(String eventType, String requestId, OrderView order) {
    int version = 1;
    localEventMapper.insert(idGenerator.next("fulfillmentLocalEvent"),
        EventKey.of("fulfillment", eventType, order.orderId(), version),
        eventType,
        version,
        "ORDER",
        order.orderId(),
        toJson(eventPayload(requestId, order)),
        LocalEventStatus.NEW.name(),
        LocalDateTime.now());
  }

  private Map<String, Object> eventPayload(String requestId, OrderView order) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("requestId", requestId);
    payload.put("orderId", order.orderId());
    payload.put("userId", order.userId());
    payload.put("merchantId", order.merchantId());
    payload.put("status", order.status());
    payload.put("queueTicketId", order.queueTicketId());
    payload.put("capacityTokenId", order.capacityTokenId());
    payload.put("payOrderId", order.payOrderId());
    payload.put("amountCent", order.amountCent());
    return payload;
  }

  private LocalEventView eventView(LocalEventRow row) {
    return new LocalEventView(row.getId(), row.getEventKey(), row.getEventType(), row.getEventVersion(),
        row.getAggregateType(), row.getAggregateId(), row.getPayloadJson(), row.getStatus(), row.getRetryCount(),
        row.getLastError(), row.getCreateTime(), row.getUpdateTime());
  }

  private String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("failed to serialize fulfillment event", e);
    }
  }

  private <T> T fromJson(String value, Class<T> type) {
    try {
      return objectMapper.readValue(value, type);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("failed to deserialize fulfillment task", e);
    }
  }

  private LocalDateTime retryAt(int attempt, LocalDateTime now) {
    return now.plusSeconds(Math.min(300, 1L << Math.min(8, Math.max(0, attempt))));
  }

  private String trimError(RuntimeException ex) {
    String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
    return message.length() <= 512 ? message : message.substring(0, 512);
  }

  record ReleaseCapacityRequest(String requestId, String reason) {
  }

  record ReleaseCapacityResponse(boolean released, QueueReadyTicket readyTicket) {
  }

  record QueueReadyTicket(long ticketId, String ticketNo, long capacityTokenId, Object snapshot) {
  }
}
