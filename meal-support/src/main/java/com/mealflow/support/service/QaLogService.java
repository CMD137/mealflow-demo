package com.mealflow.support.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mealflow.support.SupportDatabaseIdGenerator;
import com.mealflow.support.dto.Citation;
import com.mealflow.support.mapper.SupportQaLogMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * A2: persists one QA row per chat round. A logging failure must never fail the answer,
 * so every insert is best-effort.
 */
@Service
public class QaLogService {
  private static final Logger log = LoggerFactory.getLogger(QaLogService.class);

  private final SupportQaLogMapper qaLogMapper;
  private final SupportDatabaseIdGenerator idGenerator;
  private final ObjectMapper objectMapper;

  public QaLogService(SupportQaLogMapper qaLogMapper, SupportDatabaseIdGenerator idGenerator,
      ObjectMapper objectMapper) {
    this.qaLogMapper = qaLogMapper;
    this.idGenerator = idGenerator;
    this.objectMapper = objectMapper;
  }

  public void record(SessionContextStore.SessionContext session, String question, String answer,
      List<String> usedTools, List<Citation> citations, Long llmElapsedMs, Long toolElapsedMs,
      String modelName) {
    try {
      qaLogMapper.insert(idGenerator.next("supportQaLog"), session.sessionId(), session.traceId(),
          session.userId(), session.role(), question, answer, toJson(usedTools), toJson(citations),
          llmElapsedMs, toolElapsedMs, modelName, LocalDateTime.now());
    } catch (RuntimeException ex) {
      log.warn("failed to persist support QA log for session {}", session.sessionId(), ex);
    }
  }

  private String toJson(Object value) {
    if (value == null) {
      return null;
    }
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException ex) {
      return null;
    }
  }
}
