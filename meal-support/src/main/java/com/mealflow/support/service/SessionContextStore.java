package com.mealflow.support.service;

import com.mealflow.common.api.ErrorCode;
import com.mealflow.common.exception.BizException;
import com.mealflow.support.dto.CurrentUserContext;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Server-side session store for the support chat. Identity is captured once from gateway-injected
 * headers at session creation and is never taken from model-supplied arguments afterwards.
 */
@Component
public class SessionContextStore {
  private static final SecureRandom RANDOM = new SecureRandom();

  private final ConcurrentHashMap<String, SessionContext> sessions = new ConcurrentHashMap<>();
  private final Duration ttl;

  public SessionContextStore(@Value("${mealflow.support.session.ttl-seconds:3600}") long ttlSeconds) {
    this.ttl = Duration.ofSeconds(Math.max(1, ttlSeconds));
  }

  public SessionContext create(long userId, String role, List<String> permissions, String traceId, String channel) {
    String sessionId = newSessionId();
    SessionContext context = new SessionContext(sessionId, userId, role, permissions, traceId, channel,
        Instant.now(), Instant.now());
    sessions.put(sessionId, context);
    return context;
  }

  public SessionContext getRequired(String sessionId) {
    if (sessionId == null || sessionId.isBlank()) {
      throw new BizException(ErrorCode.BAD_REQUEST, "session id is required");
    }
    SessionContext context = sessions.get(sessionId);
    if (context == null) {
      throw new BizException(ErrorCode.NOT_FOUND, "support session not found or expired");
    }
    Instant now = Instant.now();
    if (now.isAfter(context.lastAccessAt().plus(ttl))) {
      sessions.remove(sessionId);
      throw new BizException(ErrorCode.NOT_FOUND, "support session not found or expired");
    }
    context.touch(now);
    return context;
  }

  public void ensureOwnedBy(SessionContext session, CurrentUserContext user) {
    if (session.userId() != user.userId()) {
      throw new BizException(ErrorCode.FORBIDDEN, "support session does not belong to current user");
    }
  }

  private String newSessionId() {
    byte[] bytes = new byte[24];
    RANDOM.nextBytes(bytes);
    return "sess_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  /** Immutable identity plus a mutable last-access timestamp for lazy expiry. */
  public static final class SessionContext {
    private final String sessionId;
    private final long userId;
    private final String role;
    private final List<String> permissions;
    private final String traceId;
    private final String channel;
    private final Instant createdAt;
    private volatile Instant lastAccessAt;

    SessionContext(String sessionId, long userId, String role, List<String> permissions, String traceId,
        String channel, Instant createdAt, Instant lastAccessAt) {
      this.sessionId = sessionId;
      this.userId = userId;
      this.role = role;
      this.permissions = permissions;
      this.traceId = traceId;
      this.channel = channel;
      this.createdAt = createdAt;
      this.lastAccessAt = lastAccessAt;
    }

    public String sessionId() { return sessionId; }
    public long userId() { return userId; }
    public String role() { return role; }
    public List<String> permissions() { return permissions; }
    public String traceId() { return traceId; }
    public String channel() { return channel; }
    public Instant createdAt() { return createdAt; }

    void touch(Instant now) {
      this.lastAccessAt = now;
    }

    Instant lastAccessAt() {
      return lastAccessAt;
    }

    public Map<String, Object> asPublicMap() {
      return Map.of("sessionId", sessionId, "userId", userId, "role", role, "channel", channel);
    }
  }
}
