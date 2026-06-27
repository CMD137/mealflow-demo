package com.mealflow.fulfillment;

import com.mealflow.common.api.Result;
import com.mealflow.fulfillment.api.OrderView;
import com.mealflow.fulfillment.config.ServiceEndpoints;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class FulfillmentService {
  private final RestTemplate restTemplate;
  private final ServiceEndpoints endpoints;

  public FulfillmentService(RestTemplate restTemplate, ServiceEndpoints endpoints) {
    this.restTemplate = restTemplate;
    this.endpoints = endpoints;
  }

  public OrderView accept(long orderId) {
    return postOrder(orderId, "merchant-accept");
  }

  public OrderView mealReady(long orderId, String requestId) {
    OrderView order = postOrder(orderId, "meal-ready");
    ReleaseCapacityResponse released = releaseCapacity(order.capacityTokenId(), requestId, "MEAL_READY");
    if (released.readyTicket() != null) {
      QueueReadyTicket ready = released.readyTicket();
      restTemplate.postForObject(endpoints.order() + "/orders/internal/from-ticket/" + ready.ticketId() + "/"
          + ready.capacityTokenId(), null, Result.class);
    }
    return order;
  }

  public OrderView pickedUp(long orderId) {
    return postOrder(orderId, "picked-up");
  }

  public OrderView delivered(long orderId) {
    return postOrder(orderId, "delivered");
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

  record ReleaseCapacityRequest(String requestId, String reason) {
  }

  record ReleaseCapacityResponse(boolean released, QueueReadyTicket readyTicket) {
  }

  record QueueReadyTicket(long ticketId, String ticketNo, long capacityTokenId, Object snapshot) {
  }
}
