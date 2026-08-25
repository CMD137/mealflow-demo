package com.mealflow.support.client;

import com.mealflow.support.dto.AgentChatRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

/** SSE streaming client to the Python agent runtime (A1). */
@Component
public class AgentRuntimeStreamClient {
  private final WebClient webClient;
  private final String streamPath;
  private final String internalToken;

  public AgentRuntimeStreamClient(WebClient.Builder webClientBuilder,
      @Value("${mealflow.support.agent-runtime.base-url:http://localhost:8090}") String baseUrl,
      @Value("${mealflow.support.agent-runtime.stream-path:/agent/chat/stream}") String streamPath,
      @Value("${mealflow.support.agent-runtime.internal-token:}") String internalToken) {
    this.webClient = webClientBuilder.baseUrl(baseUrl).build();
    this.streamPath = streamPath;
    this.internalToken = internalToken;
  }

  public Flux<ServerSentEvent<String>> stream(AgentChatRequest request) {
    return webClient.post()
        .uri(streamPath)
        .contentType(MediaType.APPLICATION_JSON)
        .headers(headers -> {
          if (internalToken != null && !internalToken.isBlank()) {
            headers.setBearerAuth(internalToken);
          }
        })
        .bodyValue(request)
        .retrieve()
        .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {
        })
        .onErrorResume(ex -> Flux.just(ServerSentEvent.builder("客服服务暂不可用，请稍后重试")
            .event("error")
            .build()));
  }
}
