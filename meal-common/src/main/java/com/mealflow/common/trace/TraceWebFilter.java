package com.mealflow.common.trace;

import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Reactive variant of {@link TraceFilter} used by the WebFlux gateway.
 *
 * <p>The gateway is the single entry point that generates (or propagates) the {@code X-Trace-Id}.
 * It injects the id into the <em>forwarded request</em> so every downstream service picks up the
 * same value and echoes it back; MDC keeps the gateway's own logs correlated. The response header
 * is intentionally not set here - the downstream service echoes the shared id, which avoids two
 * conflicting header values being merged into the final response.</p>
 */
public class TraceWebFilter implements WebFilter {

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    String incoming = exchange.getRequest().getHeaders().getFirst(TraceContext.TRACE_ID_HEADER);
    String traceId = TraceContext.start(incoming);
    ServerHttpRequest request = exchange.getRequest().mutate()
        .headers(headers -> headers.set(TraceContext.TRACE_ID_HEADER, traceId))
        .build();
    return chain.filter(exchange.mutate().request(request).build())
        .doFinally(signal -> TraceContext.clear());
  }
}
