package com.mealflow.notify;

import com.mealflow.infra.id.IdGenerator;
import com.mealflow.infra.consumer.PersistentConsumerRecordTemplate;
import com.mealflow.notify.api.ConsumerRecordView;
import com.mealflow.notify.api.MessageView;
import com.mealflow.notify.api.PushMessageRequest;
import com.mealflow.notify.mapper.ConsumerRecordMapper;
import com.mealflow.notify.mapper.ConsumerRecordRow;
import com.mealflow.notify.mapper.NotifyMapper;
import com.mealflow.notify.mapper.NotifyMessageRow;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotifyService {
  private final IdGenerator idGenerator = new IdGenerator();
  private final NotifyMapper notifyMapper;
  private final ConsumerRecordMapper consumerRecordMapper;
  private final PersistentConsumerRecordTemplate consumerRecordTemplate;

  public NotifyService(NotifyMapper notifyMapper, ConsumerRecordMapper consumerRecordMapper) {
    this.notifyMapper = notifyMapper;
    this.consumerRecordMapper = consumerRecordMapper;
    this.consumerRecordTemplate = new PersistentConsumerRecordTemplate(consumerRecordMapper);
  }

  @Transactional
  public MessageView push(PushMessageRequest request) {
    long id = idGenerator.next("notifyMessage");
    LocalDateTime createTime = LocalDateTime.now();
    notifyMapper.insert(id, request.userId(), request.bizType(), request.content(), createTime);
    return new MessageView(id, request.userId(), request.bizType(), request.content(), createTime);
  }

  @Transactional
  public MessageView pushOnce(String eventKey, String consumerGroup, PushMessageRequest request) {
    return consumerRecordTemplate.consumeOnce(eventKey, consumerGroup, () -> push(request));
  }

  public List<MessageView> list(long userId) {
    return notifyMapper.findByUser(userId).stream().map(this::view).toList();
  }

  public List<ConsumerRecordView> consumerRecords() {
    return consumerRecordMapper.findAll().stream().map(this::recordView).toList();
  }

  private MessageView view(NotifyMessageRow message) {
    return new MessageView(message.getId(), message.getUserId(), message.getBizType(), message.getContent(),
        message.getCreateTime());
  }

  private ConsumerRecordView recordView(ConsumerRecordRow row) {
    return new ConsumerRecordView(row.getId(), row.getEventKey(), row.getConsumerGroup(), row.getStatus(),
        row.getLastError(), row.getCreateTime(), row.getUpdateTime());
  }
}
