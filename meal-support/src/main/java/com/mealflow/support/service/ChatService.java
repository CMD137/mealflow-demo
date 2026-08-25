package com.mealflow.support.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mealflow.support.client.AgentRuntimeClient;
import com.mealflow.support.client.AgentRuntimeStreamClient;
import com.mealflow.support.dto.AgentChatRequest;
import com.mealflow.support.dto.AgentChatResponse;
import com.mealflow.support.dto.ChatResponse;
import com.mealflow.support.dto.Citation;
import com.mealflow.support.service.SessionContextStore.SessionContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/** Forwards chat requests to the Python agent runtime and records the QA log (A2). */
@Service
public class ChatService {
  private static final TypeReference<Map<String, Object>> DONE_PAYLOAD = new TypeReference<>() {
  };

  private final AgentRuntimeClient agentRuntimeClient;
  private final AgentRuntimeStreamClient agentRuntimeStreamClient;
  private final QaLogService qaLogService;
  private final ObjectMapper objectMapper;

  public ChatService(AgentRuntimeClient agentRuntimeClient,
      AgentRuntimeStreamClient agentRuntimeStreamClient, QaLogService qaLogService, ObjectMapper objectMapper) {
    this.agentRuntimeClient = agentRuntimeClient;
    this.agentRuntimeStreamClient = agentRuntimeStreamClient;
    this.qaLogService = qaLogService;
    this.objectMapper = objectMapper;
  }

  public ChatResponse chat(SessionContext session, String message) {
    AgentChatResponse response = agentRuntimeClient.chat(buildRequest(session, message));
    qaLogService.record(session, message, response.answer(), response.usedTools(), response.citations(),
        response.llmElapsedMs(), response.toolElapsedMs(), response.modelName());
    return new ChatResponse(session.sessionId(), response.answer(), response.usedTools(), session.traceId(),
        response.citations());
  }

  public Flux<ServerSentEvent<String>> stream(SessionContext session, String message) {
    StringBuilder answer = new StringBuilder();
    List<String> usedTools = new ArrayList<>();
    List<Citation> citations = new ArrayList<>();
    String[] modelName = new String[1];
    return agentRuntimeStreamClient.stream(buildRequest(session, message))
        .doOnNext(event -> {
          String eventName = event.event();
          if ("answer".equals(eventName)) {
            answer.append(event.data());
          } else if ("tool".equals(eventName)) {
            usedTools.add(event.data());
          } else if ("done".equals(eventName)) {
            mergeDone(event.data(), usedTools, citations, modelName);
          }
        })
        .doOnComplete(() -> qaLogService.record(session, message, answer.toString(), usedTools, citations,
            null, null, modelName[0]));
  }

  private AgentChatRequest buildRequest(SessionContext session, String message) {
    return new AgentChatRequest(session.sessionId(), message, session.traceId(), session.userId(),
        session.role(), session.permissions(), session.channel());
  }

  private void mergeDone(String data, List<String> usedTools, List<Citation> citations, String[] modelName) {
    if (data == null || data.isBlank()) {
      return;
    }
    try {
      Map<String, Object> payload = objectMapper.readValue(data, DONE_PAYLOAD);
      Object tools = payload.get("usedTools");
      if (tools instanceof List<?> list) {
        for (Object tool : list) {
          if (tool != null) {
            usedTools.add(String.valueOf(tool));
          }
        }
      }
      Object rawCitations = payload.get("citations");
      if (rawCitations instanceof List<?> list) {
        for (Object item : list) {
          if (item instanceof Citation citation) {
            citations.add(citation);
          } else if (item instanceof Map<?, ?> map) {
            citations.add(objectMapper.convertValue(map, Citation.class));
          }
        }
      }
      Object rawModel = payload.get("modelName");
      if (rawModel != null) {
        modelName[0] = String.valueOf(rawModel);
      }
    } catch (Exception ignored) {
      // done payload parsing is best-effort; logging must not fail the stream.
    }
  }
}
