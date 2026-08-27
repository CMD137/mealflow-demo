package com.mealflow.order.client;

import com.mealflow.common.api.Result;
import com.mealflow.order.config.ServiceEndpoints;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/** Reads a user-owned address once at submission time so the order can persist an immutable snapshot. */
@Component
public class AuthUserClient {
  private final RestTemplate restTemplate;
  private final ServiceEndpoints endpoints;

  public AuthUserClient(RestTemplate restTemplate, ServiceEndpoints endpoints) {
    this.restTemplate = restTemplate;
    this.endpoints = endpoints;
  }

  public AddressView address(long userId, long addressId) {
    Result<AddressView> result = restTemplate.exchange(
        endpoints.authUser() + "/auth/internal/users/" + userId + "/addresses/" + addressId,
        HttpMethod.GET, null, new ParameterizedTypeReference<Result<AddressView>>() { }).getBody();
    if (result == null || !result.success() || result.data() == null) {
      throw new IllegalArgumentException(result == null ? "address query failed" : result.message());
    }
    return result.data();
  }

  public record AddressView(long addressId, long userId, String contactName, String phone, String detail,
      boolean defaultAddress) {
  }
}
