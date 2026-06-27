package com.mealflow.order.client;

import com.mealflow.common.api.Result;
import com.mealflow.order.config.ServiceEndpoints;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class QueueClient {
  private final RestTemplate restTemplate;
  private final ServiceEndpoints endpoints;

  public QueueClient(RestTemplate restTemplate, ServiceEndpoints endpoints) {
    this.restTemplate = restTemplate;
    this.endpoints = endpoints;
  }

  public QueueApplyResponse apply(QueueApplyRequest request) {
    Result<QueueApplyResponse> result = restTemplate.exchange(
        endpoints.queue() + "/queue/internal/capacity/apply",
        HttpMethod.POST,
        new HttpEntity<>(request),
        new ParameterizedTypeReference<Result<QueueApplyResponse>>() {
        }).getBody();
    return requireData(result, "queue apply 调用失败");
  }

  public void bindOrder(long capacityTokenId, BindOrderRequest request) {
    restTemplate.postForObject(endpoints.queue() + "/queue/internal/capacity/" + capacityTokenId + "/bind-order",
        request, Result.class);
  }

  public ReleaseCapacityResponse release(long capacityTokenId, ReleaseCapacityRequest request) {
    Result<ReleaseCapacityResponse> result = restTemplate.exchange(
        endpoints.queue() + "/queue/internal/capacity/" + capacityTokenId + "/release",
        HttpMethod.POST,
        new HttpEntity<>(request),
        new ParameterizedTypeReference<Result<ReleaseCapacityResponse>>() {
        }).getBody();
    return requireData(result, "queue release 调用失败");
  }

  public QueueTicketSnapshot markProcessing(long ticketId) {
    Result<QueueTicketSnapshot> result = restTemplate.exchange(
        endpoints.queue() + "/queue/internal/tickets/" + ticketId + "/processing",
        HttpMethod.POST,
        HttpEntity.EMPTY,
        new ParameterizedTypeReference<Result<QueueTicketSnapshot>>() {
        }).getBody();
    return requireData(result, "queue processing 调用失败");
  }

  public void orderCreated(long ticketId, BindOrderRequest request) {
    restTemplate.postForObject(endpoints.queue() + "/queue/internal/tickets/" + ticketId + "/order-created",
        request, Result.class);
  }

  private static <T> T requireData(Result<T> result, String fallback) {
    if (result == null || !result.success()) {
      throw new IllegalStateException(result == null ? fallback : result.message());
    }
    return result.data();
  }

  public record QueueTicketSnapshot(List<Map<String, Object>> items, List<Long> reservationIds, Long voucherLockId,
                                    int totalAmount, String remark) {
  }

  public record QueueApplyRequest(String requestId, long userId, long merchantId, QueueTicketSnapshot snapshot,
                                  LocalDateTime expireTime, long priorityWeightMillis) {
  }

  public record QueueApplyResponse(String result, Long capacityTokenId, Long ticketId, String ticketNo, int aheadCount,
                                   int estimatedWaitSeconds, LocalDateTime expireTime) {
  }

  public record BindOrderRequest(String requestId, long orderId) {
  }

  public record ReleaseCapacityRequest(String requestId, String reason) {
  }

  public record ReleaseCapacityResponse(boolean released, QueueReadyTicket readyTicket) {
  }

  public record QueueReadyTicket(long ticketId, String ticketNo, long capacityTokenId, QueueTicketSnapshot snapshot) {
  }
}
