package com.mealflow.common.trace;

import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Reactive variant of {@link TraceFilter} used by the WebFlux gateway so that the gateway is the
 * single entry point that generates or propagates the {@code X-Trace-Id} for every downstream hop.
 */
public class TraceWebFilter implements WebFilter {

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    String incoming = exchange.getRequest().getHeaders().getFirst(TraceContext.TRACE_ID_HEADER);
    String traceId = TraceContext.start(incoming);
    exchange.getResponse().getHeaders().set(TraceContext.TRACE_ID_HEADER, traceId);
    return chain.filter(exchange).doFinally(signal -> TraceContext.clear());
  }
}
