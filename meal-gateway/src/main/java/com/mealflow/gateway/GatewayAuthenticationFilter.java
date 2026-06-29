package com.mealflow.gateway;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class GatewayAuthenticationFilter implements GlobalFilter, Ordered {
  private static final String USER_ID_HEADER = "X-User-Id";
  private static final String ROLE_HEADER = "X-Role";
  private static final String MERCHANT_ID_HEADER = "X-Merchant-Id";
  private static final String PERMISSIONS_HEADER = "X-Permissions";

  private final WebClient webClient;
  private final boolean enabled;
  private final Duration timeout;

  public GatewayAuthenticationFilter(WebClient.Builder webClientBuilder,
      @Value("${mealflow.gateway.auth.auth-user-uri:http://localhost:8101}") String authUserUri,
      @Value("${mealflow.gateway.auth.enabled:true}") boolean enabled,
      @Value("${mealflow.gateway.auth.timeout-ms:3000}") long timeoutMs) {
    this.webClient = webClientBuilder.baseUrl(authUserUri).build();
    this.enabled = enabled;
    this.timeout = Duration.ofMillis(timeoutMs);
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    ServerHttpRequest request = exchange.getRequest();
    if (!enabled || isPublicRequest(request)) {
      return chain.filter(stripIdentityHeaders(exchange));
    }

    String token = bearerToken(request);
    if (token == null) {
      return writeError(exchange, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "missing bearer token");
    }

    return validateToken(token)
        .timeout(timeout)
        .flatMap(result -> {
          if (result == null || !result.success() || result.data() == null) {
            return writeError(exchange, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "invalid token");
          }
          String requiredPermission = requiredPermission(request);
          if (requiredPermission != null && !result.data().permissions().contains(requiredPermission)) {
            return writeError(exchange, HttpStatus.FORBIDDEN, "FORBIDDEN", "permission denied");
          }
          return chain.filter(withPrincipal(exchange, result.data()));
        })
        .onErrorResume(ex -> writeError(exchange, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "auth service unavailable"));
  }

  @Override
  public int getOrder() {
    return -100;
  }

  private boolean isPublicRequest(ServerHttpRequest request) {
    String path = request.getURI().getPath();
    HttpMethod method = request.getMethod();
    return path.equals("/ping")
        || path.equals("/auth/login")
        || path.equals("/auth/ping")
        || path.equals("/actuator/health")
        || (HttpMethod.GET.equals(method) && path.endsWith("/ping"))
        || (HttpMethod.GET.equals(method) && path.startsWith("/catalog/"));
  }

  private String bearerToken(ServerHttpRequest request) {
    String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
    if (authorization == null || !authorization.startsWith("Bearer ")) {
      return null;
    }
    String token = authorization.substring("Bearer ".length()).trim();
    return token.isEmpty() ? null : token;
  }

  private Mono<AuthResult> validateToken(String token) {
    return webClient.post()
        .uri("/auth/internal/tokens/validate")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(new TokenValidationRequest(token))
        .retrieve()
        .bodyToMono(AuthResult.class);
  }

  private ServerWebExchange stripIdentityHeaders(ServerWebExchange exchange) {
    ServerHttpRequest request = exchange.getRequest().mutate()
        .headers(headers -> {
          headers.remove(USER_ID_HEADER);
          headers.remove(ROLE_HEADER);
          headers.remove(MERCHANT_ID_HEADER);
          headers.remove(PERMISSIONS_HEADER);
        })
        .build();
    return exchange.mutate().request(request).build();
  }

  private ServerWebExchange withPrincipal(ServerWebExchange exchange, TokenPrincipal principal) {
    ServerHttpRequest request = exchange.getRequest().mutate()
        .headers(headers -> {
          headers.remove(USER_ID_HEADER);
          headers.remove(ROLE_HEADER);
          headers.remove(MERCHANT_ID_HEADER);
          headers.remove(PERMISSIONS_HEADER);
          headers.add(USER_ID_HEADER, Long.toString(principal.userId()));
          headers.add(ROLE_HEADER, principal.roleCode());
          if (principal.merchantId() != null) {
            headers.add(MERCHANT_ID_HEADER, Long.toString(principal.merchantId()));
          }
          headers.add(PERMISSIONS_HEADER, String.join(",", principal.permissions()));
        })
        .build();
    return exchange.mutate().request(request).build();
  }

  private String requiredPermission(ServerHttpRequest request) {
    String path = request.getURI().getPath();
    HttpMethod method = request.getMethod();
    if (path.contains("/internal/")) {
      return "INTERNAL_OPERATE";
    }
    if (path.startsWith("/auth/admin/")) {
      return "MERCHANT_MANAGE";
    }
    if (path.startsWith("/fulfillment/")) {
      return "FULFILLMENT_OPERATE";
    }
    if (path.startsWith("/catalog/admin/")) {
      return "CATALOG_MANAGE";
    }
    if (path.startsWith("/merchant/") || path.startsWith("/merchants/")) {
      return "MERCHANT_MANAGE";
    }
    if (path.startsWith("/queue/merchants/") && !HttpMethod.GET.equals(method)) {
      return "MERCHANT_MANAGE";
    }
    if (path.startsWith("/payments/")) {
      return "PAYMENT_WRITE";
    }
    if (path.startsWith("/orders/")) {
      return "ORDER_WRITE";
    }
    if (path.startsWith("/cart/")) {
      return "CART_WRITE";
    }
    if (path.startsWith("/vouchers/")) {
      return "VOUCHER_USE";
    }
    if (path.startsWith("/notify/")) {
      return "NOTIFY_READ";
    }
    if (path.startsWith("/users/")) {
      return "USER_READ";
    }
    return null;
  }

  private Mono<Void> writeError(ServerWebExchange exchange, HttpStatus status, String code, String message) {
    String body = """
        {"success":false,"code":"%s","message":"%s","data":null}
        """.formatted(code, message).trim();
    exchange.getResponse().setStatusCode(status);
    exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(bytes)));
  }

  private record TokenValidationRequest(String token) {
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record AuthResult(boolean success, String code, String message, TokenPrincipal data) {
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record TokenPrincipal(long userId, String phone, String nickname, String roleCode, Long merchantId,
      List<String> permissions) {
  }
}
