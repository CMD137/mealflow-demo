package com.mealflow.order.client;

import com.mealflow.common.api.Result;
import com.mealflow.order.config.ServiceEndpoints;
import java.time.LocalDateTime;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class PromotionClient {
  private final RestTemplate restTemplate;
  private final ServiceEndpoints endpoints;

  public PromotionClient(RestTemplate restTemplate, ServiceEndpoints endpoints) {
    this.restTemplate = restTemplate;
    this.endpoints = endpoints;
  }

  public VoucherLockResponse lock(LockVoucherRequest request) {
    Result<VoucherLockResponse> result = restTemplate.exchange(
        endpoints.promotion() + "/vouchers/internal/lock",
        HttpMethod.POST,
        new HttpEntity<>(request),
        new ParameterizedTypeReference<Result<VoucherLockResponse>>() {
        }).getBody();
    if (result == null || !result.success()) {
      throw new IllegalStateException(result == null ? "promotion 调用失败" : result.message());
    }
    return result.data();
  }

  public void confirm(VoucherTransitionRequest request) {
    restTemplate.postForObject(endpoints.promotion() + "/vouchers/internal/confirm", request, Result.class);
  }

  public void release(VoucherTransitionRequest request) {
    restTemplate.postForObject(endpoints.promotion() + "/vouchers/internal/release", request, Result.class);
  }

  public record LockVoucherRequest(String requestId, long userId, Long userVoucherId, Long ticketId, Long orderId,
                                   LocalDateTime lockExpireTime) {
  }

  public record VoucherLockResponse(Long voucherLockId, String status, int discountAmount) {
  }

  public record VoucherTransitionRequest(String requestId, Long voucherLockId, Long orderId, String reason) {
  }
}
