package com.mealflow.common.internal;

import com.mealflow.common.trace.TraceContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Validates the {@code X-Internal-*} HMAC signature on every non-public request received by a
 * business service.
 *
 * <p>Policy: requests matching {@link InternalAuthProperties#isPublicPath(String)} pass through;
 * every other request must carry a valid signature from the gateway or a peer service, inside the
 * timestamp window, with a non-replayed nonce. This is the mechanism that makes gateway-injected
 * {@code X-User-Id}/{@code X-Merchant-Id} headers trustworthy at the service layer and closes the
 * "forged identity header" hole for internal endpoints.</p>
 */
public class InternalAuthFilter extends OncePerRequestFilter {

  private final InternalAuthProperties properties;
  private final NonceCache nonceCache;

  public InternalAuthFilter(InternalAuthProperties properties) {
    this.properties = properties;
    this.nonceCache = new NonceCache(properties.getNonceCacheCapacity(),
        properties.getTimestampWindowSeconds() * 1000L);
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !properties.isConfigured() || properties.isPublicPath(request.getRequestURI());
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String service = request.getHeader(InternalSignature.HEADER_SERVICE);
    String timestamp = request.getHeader(InternalSignature.HEADER_TIMESTAMP);
    String nonce = request.getHeader(InternalSignature.HEADER_NONCE);
    String signature = request.getHeader(InternalSignature.HEADER_SIGNATURE);

    if (!isFresh(timestamp) || !nonceCache.accept(service, nonce, parseTimestamp(timestamp))) {
      reject(response, "missing or expired internal signature");
      return;
    }
    String method = request.getMethod();
    String rawPath = request.getRequestURI();
    String rawQuery = request.getQueryString();
    if (!InternalSignature.verify(properties.getSecret(), service, method, rawPath, rawQuery, timestamp, nonce,
        signature)) {
      reject(response, "invalid internal signature");
      return;
    }
    filterChain.doFilter(request, response);
  }

  private boolean isFresh(String timestamp) {
    if (timestamp == null || timestamp.isBlank()) {
      return false;
    }
    try {
      long value = Long.parseLong(timestamp.trim());
      return Math.abs(System.currentTimeMillis() - value) <= properties.getTimestampWindowSeconds() * 1000L;
    } catch (NumberFormatException ex) {
      return false;
    }
  }

  private long parseTimestamp(String timestamp) {
    try {
      return Long.parseLong(timestamp.trim());
    } catch (NumberFormatException ex) {
      return 0L;
    }
  }

  private void reject(HttpServletResponse response, String message) throws IOException {
    response.setStatus(HttpStatus.UNAUTHORIZED.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    String traceId = TraceContext.current();
    String body = """
        {"success":false,"code":"UNAUTHORIZED","message":"%s","data":null}
        """.formatted(message).trim();
    if (traceId != null) {
      response.setHeader(TraceContext.TRACE_ID_HEADER, traceId);
    }
    response.getWriter().write(body);
  }
}
