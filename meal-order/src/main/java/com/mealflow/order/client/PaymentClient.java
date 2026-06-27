package com.mealflow.order.client;

import com.mealflow.common.api.Result;
import com.mealflow.order.config.ServiceEndpoints;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class PaymentClient {
  private final RestTemplate restTemplate;
  private final ServiceEndpoints endpoints;

  public PaymentClient(RestTemplate restTemplate, ServiceEndpoints endpoints) {
    this.restTemplate = restTemplate;
    this.endpoints = endpoints;
  }

  public PaymentView create(CreatePaymentRequest request) {
    Result<PaymentView> result = restTemplate.exchange(
        endpoints.payment() + "/payments/internal/create",
        HttpMethod.POST,
        new HttpEntity<>(request),
        new ParameterizedTypeReference<Result<PaymentView>>() {
        }).getBody();
    if (result == null || !result.success()) {
      throw new IllegalStateException(result == null ? "payment 调用失败" : result.message());
    }
    return result.data();
  }

  public void close(long payOrderId, ClosePaymentRequest request) {
    restTemplate.postForObject(endpoints.payment() + "/payments/" + payOrderId + "/close", request, Result.class);
  }

  public record CreatePaymentRequest(String requestId, long orderId, int amountCent) {
  }

  public record ClosePaymentRequest(String requestId, String reason) {
  }

  public record PaymentView(long payOrderId, long orderId, int amountCent, String status) {
  }
}
