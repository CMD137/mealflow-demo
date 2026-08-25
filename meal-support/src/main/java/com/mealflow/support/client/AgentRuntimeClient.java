package com.mealflow.support.client;

import com.mealflow.common.api.ErrorCode;
import com.mealflow.common.exception.BizException;
import com.mealflow.support.dto.AgentChatRequest;
import com.mealflow.support.dto.AgentChatResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Synchronous chat client to the Python agent runtime (Bearer token, A4 Java -> Python). */
@Component
public class AgentRuntimeClient {
  private final RestClient restClient;
  private final String chatPath;
  private final String internalToken;

  public AgentRuntimeClient(RestClient restClient,
      @Value("${mealflow.support.agent-runtime.base-url:http://localhost:8090}") String baseUrl,
      @Value("${mealflow.support.agent-runtime.chat-path:/agent/chat}") String chatPath,
      @Value("${mealflow.support.agent-runtime.internal-token:}") String internalToken) {
    // Keep the configured connect/read timeouts from HttpClientConfig.
    this.restClient = restClient.mutate().baseUrl(baseUrl).build();
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
