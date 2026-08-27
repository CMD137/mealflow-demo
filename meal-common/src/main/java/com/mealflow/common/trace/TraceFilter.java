package com.mealflow.common.trace;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Servlet filter that starts an MDC trace scope for every incoming request and echoes the trace
 * id back in the {@code X-Trace-Id} response header. Registered automatically for Spring MVC
 * services via {@code TraceMvcAutoConfiguration}.
 */
public class TraceFilter extends OncePerRequestFilter {

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String traceId = TraceContext.start(request.getHeader(TraceContext.TRACE_ID_HEADER));
    response.setHeader(TraceContext.TRACE_ID_HEADER, traceId);
    try {
      filterChain.doFilter(request, response);
    } finally {
      TraceContext.clear();
    }
  }
}
