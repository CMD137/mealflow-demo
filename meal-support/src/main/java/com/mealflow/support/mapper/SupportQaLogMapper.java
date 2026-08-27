package com.mealflow.support.mapper;

import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SupportQaLogMapper {
  @Insert("""
      INSERT INTO meal_support_qa_log (
        id, session_id, trace_id, user_id, role, question, answer, used_tools, citations,
        llm_elapsed_ms, tool_elapsed_ms, model_name, create_time
      ) VALUES (
        #{id}, #{sessionId}, #{traceId}, #{userId}, #{role}, #{question}, #{answer}, #{usedTools},
        #{citations}, #{llmElapsedMs}, #{toolElapsedMs}, #{modelName}, #{now}
      )
      """)
  int insert(@Param("id") long id, @Param("sessionId") String sessionId, @Param("traceId") String traceId,
      @Param("userId") long userId, @Param("role") String role, @Param("question") String question,
      @Param("answer") String answer, @Param("usedTools") String usedTools, @Param("citations") String citations,
      @Param("llmElapsedMs") Long llmElapsedMs, @Param("toolElapsedMs") Long toolElapsedMs,
      @Param("modelName") String modelName, @Param("now") LocalDateTime now);
}
