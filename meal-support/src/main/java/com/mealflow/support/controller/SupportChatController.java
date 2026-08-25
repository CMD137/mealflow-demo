package com.mealflow.support.controller;

import com.mealflow.common.api.Result;
import com.mealflow.common.security.RequestIdentity;
import com.mealflow.support.dto.ChatRequest;
import com.mealflow.support.dto.ChatResponse;
import com.mealflow.support.dto.CreateSessionRequest;
import com.mealflow.support.dto.CreateSessionResponse;
import com.mealflow.support.dto.CurrentUserContext;
import com.mealflow.support.dto.SupportPingView;
import com.mealflow.support.service.ChatService;
import com.mealflow.support.service.SessionContextStore;
import com.mealflow.support.service.SessionContextStore.SessionContext;
import com.mealflow.support.service.ToolRegistryService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/support")
public class SupportChatController {
  private final SessionContextStore sessionStore;
  private final ToolRegistryService toolRegistryService;
  private final ChatService chatService;

  public SupportChatController(SessionContextStore sessionStore, ToolRegistryService toolRegistryService,
      ChatService chatService) {
    this.sessionStore = sessionStore;
    this.toolRegistryService = toolRegistryService;
    this.chatService = chatService;
  }

  @GetMapping("/ping")
  public Result<SupportPingView> ping() {
    return Result.ok(new SupportPingView("meal-support", toolRegistryService.all()));
  }

  @PostMapping("/session")
  public Result<CreateSessionResponse> createSession(@Valid @RequestBody CreateSessionRequest request,
      @RequestHeader(value = "X-User-Id", required = false) Long userId,
      @RequestHeader(value = "X-Role", required = false) String role,
      @RequestHeader(value = "X-Permissions", required = false) String permissions,
      @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
    SessionContext session = sessionStore.create(RequestIdentity.requireUser(userId),
        role == null || role.isBlank() ? "CUSTOMER" : role,
        permissions == null ? List.of() : List.of(permissions.split(",")),
        traceId == null || traceId.isBlank() ? UUID.randomUUID().toString() : traceId,
        request.channel());
    return Result.ok(new CreateSessionResponse(session.sessionId(), session.traceId()));
  }

  @PostMapping("/chat")
  public Result<ChatResponse> chat(@Valid @RequestBody ChatRequest request,
      @RequestHeader(value = "X-User-Id", required = false) Long userId) {
    SessionContext session = requireOwnedSession(request.sessionId(), userId);
    return Result.ok(chatService.chat(session, request.message()));
  }

  @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public Flux<ServerSentEvent<String>> stream(@Valid @RequestBody ChatRequest request,
      @RequestHeader(value = "X-User-Id", required = false) Long userId) {
    SessionContext session = requireOwnedSession(request.sessionId(), userId);
    return chatService.stream(session, request.message());
  }

  private SessionContext requireOwnedSession(String sessionId, Long userId) {
    CurrentUserContext user = new CurrentUserContext(RequestIdentity.requireUser(userId), null, null, null);
    SessionContext session = sessionStore.getRequired(sessionId);
    sessionStore.ensureOwnedBy(session, user);
    return session;
  }
}
