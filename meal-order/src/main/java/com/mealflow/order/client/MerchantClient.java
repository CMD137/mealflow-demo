package com.mealflow.order.client;

import com.mealflow.common.api.ErrorCode;
import com.mealflow.common.api.Result;
import com.mealflow.common.exception.BizException;
import com.mealflow.order.config.ServiceEndpoints;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class MerchantClient {
  private final RestTemplate restTemplate;
  private final ServiceEndpoints endpoints;

  public MerchantClient(RestTemplate restTemplate, ServiceEndpoints endpoints) {
    this.restTemplate = restTemplate;
    this.endpoints = endpoints;
  }

  public MerchantView requireAcceptingOrders(long merchantId) {
    Result<MerchantView> result = restTemplate.exchange(endpoints.merchant() + "/merchants/" + merchantId,
        HttpMethod.GET, null, new ParameterizedTypeReference<Result<MerchantView>>() { }).getBody();
    if (result == null || !result.success() || result.data() == null) {
      throw new BizException(ErrorCode.NOT_FOUND, "merchant not found");
    }
    if (!"OPEN".equals(result.data().businessStatus())) {
      throw new BizException(ErrorCode.ILLEGAL_STATUS, "merchant is not accepting new orders");
    }
    return result.data();
  }

  public record MerchantView(long merchantId, String name, String businessStatus, int baseCapacity, double manualFactor) {
  }
}
