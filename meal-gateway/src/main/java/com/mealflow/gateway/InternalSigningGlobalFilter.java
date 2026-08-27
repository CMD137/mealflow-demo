package com.mealflow.gateway;

import com.mealflow.common.internal.InternalRequestSigner;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Signs every request the gateway forwards to a business service with the shared internal HMAC
 * secret. Downstream services accept gateway-injected {@code X-User-Id}/{@code X-Merchant-Id}
 * headers only because this signature proves the request really came from the gateway.
 *
 * <p>Runs after {@link GatewayAuthenticationFilter} (order -100) so identity headers are already
 * attached when the signature is computed over method + raw path + raw query.</p>
 */
@Component
public class InternalSigningGlobalFilter implements GlobalFilter, Ordered {

  private final InternalRequestSigner signer;

  public InternalSigningGlobalFilter(InternalRequestSigner signer) {
    this.signer = signer;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    if (!signer.isConfigured()) {
      return chain.filter(exchange);
    }
    ServerHttpRequest request = exchange.getRequest();
    String rawPath = request.getURI().getRawPath() != null ? request.getURI().getRawPath() : request.getURI().getPath();
    String rawQuery = request.getURI().getRawQuery();
    String method = request.getMethod() == null ? "GET" : request.getMethod().name();
    ServerHttpRequest signed = request.mutate()
        .headers(headers -> signer.sign(headers, method, rawPath, rawQuery))
        .build();
    return chain.filter(exchange.mutate().request(signed).build());
  }

  @Override
  public int getOrder() {
    return -90;
  }
}
