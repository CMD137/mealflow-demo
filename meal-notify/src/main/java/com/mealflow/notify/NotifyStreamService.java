package com.mealflow.notify;

import com.mealflow.notify.api.MessageView;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class NotifyStreamService {
  private final Map<Long, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();
  private final long timeoutMillis;

  public NotifyStreamService(@Value("${mealflow.notify.sse-timeout-ms:300000}") long timeoutMillis) {
    this.timeoutMillis = timeoutMillis;
  }

  public SseEmitter subscribe(long userId) {
    SseEmitter emitter = new SseEmitter(timeoutMillis);
    emitters.computeIfAbsent(userId, ignored -> new CopyOnWriteArrayList<>()).add(emitter);
    emitter.onCompletion(() -> remove(userId, emitter));
    emitter.onTimeout(() -> remove(userId, emitter));
    emitter.onError(ignored -> remove(userId, emitter));
    sendConnectedEvent(userId, emitter);
    return emitter;
  }

  public void publish(MessageView message) {
    List<SseEmitter> userEmitters = emitters.get(message.userId());
    if (userEmitters == null || userEmitters.isEmpty()) {
      return;
    }
    for (SseEmitter emitter : userEmitters) {
      try {
        emitter.send(SseEmitter.event()
            .name("message")
            .id(String.valueOf(message.messageId()))
            .data(message));
      } catch (IOException | RuntimeException ex) {
        remove(message.userId(), emitter);
      }
    }
  }

  public int activeConnections(long userId) {
    List<SseEmitter> userEmitters = emitters.get(userId);
    return userEmitters == null ? 0 : userEmitters.size();
  }

  public void disconnect(long userId) {
    List<SseEmitter> userEmitters = emitters.remove(userId);
    if (userEmitters == null) {
      return;
    }
    userEmitters.forEach(SseEmitter::complete);
  }

  private void sendConnectedEvent(long userId, SseEmitter emitter) {
    try {
      emitter.send(SseEmitter.event().name("connected").data(Map.of("userId", userId)));
    } catch (IOException | RuntimeException ex) {
      remove(userId, emitter);
    }
  }

  private void remove(long userId, SseEmitter emitter) {
    List<SseEmitter> userEmitters = emitters.get(userId);
    if (userEmitters == null) {
      return;
    }
    userEmitters.remove(emitter);
    if (userEmitters.isEmpty()) {
      emitters.remove(userId, userEmitters);
    }
  }
}
