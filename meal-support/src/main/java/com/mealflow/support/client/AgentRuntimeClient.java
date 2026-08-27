package com.mealflow.support.client;

import com.mealflow.common.api.ErrorCode;
import com.mealflow.common.exception.BizException;
import com.mealflow.support.dto.AgentChatRequest;
import com.mealflow.support.dto.AgentChatResponse;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Synchronous chat client to the Python agent runtime (Bearer token, A4 Java -> Python). */
@Component
public class AgentRuntimeClient {
  private final RestClient restClient;
  private final String chatPath;
  private final String internalToken;

  public AgentRuntimeClient(
      @Value("${mealflow.support.agent-runtime.base-url:http://localhost:8090}") String baseUrl,
      @Value("${mealflow.support.agent-runtime.chat-path:/agent/chat}") String chatPath,
      @Value("${mealflow.support.agent-runtime.internal-token:}") String internalToken,
      @Value("${mealflow.support.agent-runtime.timeout-ms:60000}") long timeoutMs) {
    // LLM tool loops can take tens of seconds; use a dedicated client with a generous read timeout.
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofSeconds(3));
    factory.setReadTimeout(Duration.ofMillis(Math.max(1000, timeoutMs)));
    this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    this.chatPath = chatPath;
    this.internalToken = internalToken;
  }

  public AgentChatResponse chat(AgentChatRequest request) {
    try {
      var body = restClient.post()
          .uri(chatPath)
          .contentType(MediaType.APPLICATION_JSON)
          .headers(headers -> {
            if (internalToken != null && !internalToken.isBlank()) {
              headers.setBearerAuth(internalToken);
            }
          })
          .body(request)
          .retrieve()
          .body(AgentChatResponse.class);
      if (body == null) {
        throw new BizException(ErrorCode.SYSTEM_ERROR, "agent runtime returned an empty response");
      }
      return body;
    } catch (BizException ex) {
      throw ex;
    } catch (RuntimeException ex) {
      throw new BizException(ErrorCode.SYSTEM_ERROR, "客服服务暂不可用，请稍后重试");
    }
  }
}
