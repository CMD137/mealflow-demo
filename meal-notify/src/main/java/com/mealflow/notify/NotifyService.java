package com.mealflow.notify;

import com.mealflow.infra.id.IdGenerator;
import com.mealflow.notify.api.MessageView;
import com.mealflow.notify.api.PushMessageRequest;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class NotifyService {
  private final IdGenerator idGenerator = new IdGenerator();
  private final Map<Long, MessageView> messages = new ConcurrentHashMap<>();

  public MessageView push(PushMessageRequest request) {
    long id = idGenerator.next("notifyMessage");
    MessageView message = new MessageView(id, request.userId(), request.bizType(), request.content(), LocalDateTime.now());
    messages.put(id, message);
    return message;
  }

  public List<MessageView> list(long userId) {
    return messages.values().stream()
        .filter(message -> message.userId() == userId)
        .sorted(Comparator.comparing(MessageView::createTime).reversed())
        .toList();
  }
}
