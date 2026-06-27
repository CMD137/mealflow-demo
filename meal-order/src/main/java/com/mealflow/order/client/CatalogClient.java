package com.mealflow.order.client;

import com.mealflow.common.api.Result;
import com.mealflow.order.api.OrderItemSnapshot;
import com.mealflow.order.api.OrderSkuItem;
import com.mealflow.order.config.ServiceEndpoints;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class CatalogClient {
  private final RestTemplate restTemplate;
  private final ServiceEndpoints endpoints;

  public CatalogClient(RestTemplate restTemplate, ServiceEndpoints endpoints) {
    this.restTemplate = restTemplate;
    this.endpoints = endpoints;
  }

  public List<OrderItemSnapshot> snapshots(long merchantId, List<OrderSkuItem> items) {
    Result<List<OrderItemSnapshot>> result = restTemplate.exchange(
        endpoints.catalog() + "/catalog/internal/stocks/snapshots/" + merchantId,
        HttpMethod.POST,
        new HttpEntity<>(items),
        new ParameterizedTypeReference<Result<List<OrderItemSnapshot>>>() {
        }).getBody();
    return requireData(result);
  }

  public ReserveStockResponse reserve(ReserveStockRequest request) {
    Result<ReserveStockResponse> result = restTemplate.exchange(
        endpoints.catalog() + "/catalog/internal/stocks/reserve",
        HttpMethod.POST,
        new HttpEntity<>(request),
        new ParameterizedTypeReference<Result<ReserveStockResponse>>() {
        }).getBody();
    return requireData(result);
  }

  public void confirm(StockTransitionRequest request) {
    restTemplate.postForObject(endpoints.catalog() + "/catalog/internal/stocks/confirm", request, Result.class);
  }

  public void release(StockTransitionRequest request) {
    restTemplate.postForObject(endpoints.catalog() + "/catalog/internal/stocks/release", request, Result.class);
  }

  private static <T> T requireData(Result<T> result) {
    if (result == null || !result.success()) {
      throw new IllegalStateException(result == null ? "catalog 调用失败" : result.message());
    }
    return result.data();
  }

  public record ReserveStockRequest(String requestId, long userId, long merchantId, Long ticketId, Long orderId,
                                    List<OrderSkuItem> items, LocalDateTime expireTime) {
  }

  public record ReserveStockResponse(List<Long> reservationIds, String status) {
  }

  public record StockTransitionRequest(String requestId, List<Long> reservationIds, Long orderId, String reason) {
  }
}
