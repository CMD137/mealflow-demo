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
import com.mealflow.fulfillment.outbox.OutboxEventPublisher;
import com.mealflow.infra.event.EventKey;
import com.mealflow.infra.id.IdGenerator;
import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

@Service
public class FulfillmentService {
  private final IdGenerator idGenerator = new IdGenerator();
  private final RestTemplate restTemplate;
  private final ServiceEndpoints endpoints;
  private final FulfillmentMapper fulfillmentMapper;
  private final LocalEventMapper localEventMapper;
  private final OutboxEventPublisher outboxEventPublisher;
  private final ObjectMapper objectMapper;

  public FulfillmentService(RestTemplate restTemplate, ServiceEndpoints endpoints, FulfillmentMapper fulfillmentMapper,
      LocalEventMapper localEventMapper, OutboxEventPublisher outboxEventPublisher, ObjectMapper objectMapper) {
    this.restTemplate = restTemplate;
    this.endpoints = endpoints;
    this.fulfillmentMapper = fulfillmentMapper;
    this.localEventMapper = localEventMapper;
    this.outboxEventPublisher = outboxEventPublisher;
    this.objectMapper = objectMapper;
  }

  @PostConstruct
  void initializeIdGenerator() {
    idGenerator.ensureAtLeast("fulfillmentOperation", fulfillmentMapper.maxOperationId());
    idGenerator.ensureAtLeast("localEvent", localEventMapper.maxEventId());
  }

  @Transactional
  public OrderView accept(long orderId, String requestId) {
    OrderView order = postOrder(orderId, "merchant-accept");
    recordOperation(requestId, orderId, "ACCEPT", "SUCCESS", "merchant accepted");
    appendFulfillmentEvent("FulfillmentAccepted", requestId, order);
    return order;
  }

  @Transactional
  public OrderView mealReady(long orderId, String requestId) {
    OrderView order = postOrder(orderId, "meal-ready");
    ReleaseCapacityResponse released = releaseCapacity(order.capacityTokenId(), requestId, "MEAL_READY");
    String message = released.readyTicket() == null ? "capacity released" : "capacity released and ticket promoted";
    if (released.readyTicket() != null) {
      QueueReadyTicket ready = released.readyTicket();
      restTemplate.postForObject(endpoints.order() + "/orders/internal/from-ticket/" + ready.ticketId() + "/"
          + ready.capacityTokenId(), null, Result.class);
    }
    recordOperation(requestId, orderId, "MEAL_READY", "SUCCESS", message);
    appendFulfillmentEvent("FulfillmentMealReady", requestId, order);
    return order;
  }

  @Transactional
  public OrderView pickedUp(long orderId, String requestId) {
    OrderView order = postOrder(orderId, "picked-up");
    recordOperation(requestId, orderId, "PICKED_UP", "SUCCESS", "delivery picked up");
    appendFulfillmentEvent("FulfillmentPickedUp", requestId, order);
    return order;
  }

  @Transactional
  public OrderView delivered(long orderId, String requestId) {
    OrderView order = postOrder(orderId, "delivered");
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

  private OrderView postOrder(long orderId, String action) {
    Result<OrderView> result = restTemplate.exchange(
        endpoints.order() + "/orders/" + orderId + "/" + action,
        HttpMethod.POST,
        HttpEntity.EMPTY,
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
    localEventMapper.insert(idGenerator.next("localEvent"),
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
