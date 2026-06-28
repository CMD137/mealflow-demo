package com.mealflow.fulfillment;

import com.mealflow.common.api.Result;
import com.mealflow.fulfillment.api.FulfillmentOperationView;
import com.mealflow.fulfillment.api.OrderView;
import com.mealflow.fulfillment.config.ServiceEndpoints;
import com.mealflow.fulfillment.mapper.FulfillmentMapper;
import com.mealflow.fulfillment.mapper.FulfillmentOperationRow;
import com.mealflow.infra.id.IdGenerator;
import java.time.LocalDateTime;
import java.util.List;
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

  public FulfillmentService(RestTemplate restTemplate, ServiceEndpoints endpoints, FulfillmentMapper fulfillmentMapper) {
    this.restTemplate = restTemplate;
    this.endpoints = endpoints;
    this.fulfillmentMapper = fulfillmentMapper;
  }

  @Transactional
  public OrderView accept(long orderId, String requestId) {
    OrderView order = postOrder(orderId, "merchant-accept");
    recordOperation(requestId, orderId, "ACCEPT", "SUCCESS", "merchant accepted");
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
    return order;
  }

  @Transactional
  public OrderView pickedUp(long orderId, String requestId) {
    OrderView order = postOrder(orderId, "picked-up");
    recordOperation(requestId, orderId, "PICKED_UP", "SUCCESS", "delivery picked up");
    return order;
  }

  @Transactional
  public OrderView delivered(long orderId, String requestId) {
    OrderView order = postOrder(orderId, "delivered");
    recordOperation(requestId, orderId, "DELIVERED", "SUCCESS", "delivery completed");
    return order;
  }

  public List<FulfillmentOperationView> operations() {
    return fulfillmentMapper.findOperations().stream().map(this::mapOperation).toList();
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

  record ReleaseCapacityRequest(String requestId, String reason) {
  }

  record ReleaseCapacityResponse(boolean released, QueueReadyTicket readyTicket) {
  }

  record QueueReadyTicket(long ticketId, String ticketNo, long capacityTokenId, Object snapshot) {
  }
}
