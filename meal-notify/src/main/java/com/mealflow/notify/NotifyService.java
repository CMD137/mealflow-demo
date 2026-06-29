package com.mealflow.notify;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mealflow.common.api.ErrorCode;
import com.mealflow.common.exception.BizException;
import com.mealflow.common.status.ConsumerRecordStatus;
import com.mealflow.infra.consumer.PersistentConsumerRecordTemplate;
import com.mealflow.infra.id.IdGenerator;
import com.mealflow.notify.api.ConsumerRecordView;
import com.mealflow.notify.api.DeliveryView;
import com.mealflow.notify.api.MessageView;
import com.mealflow.notify.api.PushMessageRequest;
import com.mealflow.notify.api.TemplateMessageRequest;
import com.mealflow.notify.mapper.ConsumerRecordMapper;
import com.mealflow.notify.mapper.ConsumerRecordRow;
import com.mealflow.notify.mapper.NotifyDeliveryRow;
import com.mealflow.notify.mapper.NotifyMapper;
import com.mealflow.notify.mapper.NotifyMessageRow;
import com.mealflow.notify.mapper.NotifyTemplateRow;
import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class NotifyService {
  private static final TypeReference<Map<String, Object>> EVENT_PAYLOAD = new TypeReference<>() {
  };

  private final IdGenerator idGenerator = new IdGenerator();
  private final NotifyMapper notifyMapper;
  private final ConsumerRecordMapper consumerRecordMapper;
  private final PersistentConsumerRecordTemplate consumerRecordTemplate;
  private final NotifyStreamService notifyStreamService;
  private final ObjectMapper objectMapper;

  public NotifyService(NotifyMapper notifyMapper, ConsumerRecordMapper consumerRecordMapper,
      NotifyStreamService notifyStreamService, ObjectMapper objectMapper) {
    this.notifyMapper = notifyMapper;
    this.consumerRecordMapper = consumerRecordMapper;
    this.consumerRecordTemplate = new PersistentConsumerRecordTemplate(consumerRecordMapper);
    this.notifyStreamService = notifyStreamService;
    this.objectMapper = objectMapper;
  }

  @PostConstruct
  void initializeIdGenerator() {
    ensureConsumerRecordPayloadColumns();
    idGenerator.ensureAtLeast("notifyMessage", notifyMapper.maxMessageId());
    idGenerator.ensureAtLeast("notifyDelivery", notifyMapper.maxDeliveryId());
    consumerRecordTemplate.ensureIdAtLeast(consumerRecordMapper.maxRecordId());
  }

  private void ensureConsumerRecordPayloadColumns() {
    if (consumerRecordMapper.countColumn("event_type") == 0) {
      consumerRecordMapper.addEventTypeColumn();
    }
    if (consumerRecordMapper.countColumn("payload_json") == 0) {
      consumerRecordMapper.addPayloadJsonColumn();
    }
  }

  @Transactional
  public MessageView push(PushMessageRequest request) {
    long id = idGenerator.next("notifyMessage");
    LocalDateTime createTime = LocalDateTime.now();
    notifyMapper.insert(id, request.userId(), request.bizType(), request.content(), createTime);
    MessageView message = new MessageView(id, request.userId(), request.bizType(), request.content(), createTime);
    afterCommitOrNow(() -> notifyStreamService.publish(message));
    return message;
  }

  @Transactional
  public MessageView pushTemplated(String templateCode, TemplateMessageRequest request) {
    NotifyTemplateRow template = notifyMapper.findTemplate(templateCode);
    if (template == null || !template.isEnabled()) {
      throw new BizException(ErrorCode.NOT_FOUND, "notify template not available: " + templateCode);
    }
    String content = render(template.getContentTemplate(), request.variables());
    MessageView message = push(new PushMessageRequest(request.userId(), template.getBizType(), content));
    createDeliveries(message, template.getChannels(), request.targetPhone());
    return message;
  }

  @Transactional
  public MessageView pushOnce(String eventKey, String consumerGroup, PushMessageRequest request) {
    return consumerRecordTemplate.consumeOnce(eventKey, consumerGroup, () -> push(request));
  }

  @Transactional
  public MessageView pushFromDomainEvent(String eventKey, String consumerGroup, String eventType, String payloadJson) {
    return consumerRecordTemplate.consumeOnce(eventKey, consumerGroup, eventType, payloadJson, () -> {
      Map<String, Object> payload = fromJson(payloadJson);
      long userId = longNumber(payload.get("userId"));
      if (userId <= 0) {
        return null;
      }
      String content = content(eventType, longNumber(payload.get("orderId")));
      if (content == null) {
        return null;
      }
      return push(new PushMessageRequest(userId, "ORDER", content));
    });
  }

  public List<MessageView> list(long userId) {
    return notifyMapper.findByUser(userId).stream().map(this::view).toList();
  }

  public List<DeliveryView> deliveries(long userId) {
    return notifyMapper.findDeliveriesByUser(userId).stream().map(this::deliveryView).toList();
  }

  public List<ConsumerRecordView> consumerRecords() {
    return consumerRecordMapper.findAll().stream().map(this::recordView).toList();
  }

  public int recoverTimedOutConsumerRecords() {
    return consumerRecordTemplate.recoverProcessingTimeouts();
  }

  @Transactional
  public MessageView replayDomainConsumerRecord(String eventKey, String consumerGroup) {
    ConsumerRecordRow row = consumerRecordMapper.findByEvent(eventKey, consumerGroup);
    if (row == null) {
      throw new BizException(ErrorCode.NOT_FOUND, "consumer record not found");
    }
    if (ConsumerRecordStatus.SUCCESS.name().equals(row.getStatus())) {
      return null;
    }
    if (row.getEventType() == null || row.getEventType().isBlank()
        || row.getPayloadJson() == null || row.getPayloadJson().isBlank()) {
      throw new BizException(ErrorCode.BAD_REQUEST, "consumer record payload is not replayable");
    }
    return pushFromDomainEvent(eventKey, consumerGroup, row.getEventType(), row.getPayloadJson());
  }

  private MessageView view(NotifyMessageRow message) {
    return new MessageView(message.getId(), message.getUserId(), message.getBizType(), message.getContent(),
        message.getCreateTime());
  }

  private DeliveryView deliveryView(NotifyDeliveryRow row) {
    return new DeliveryView(row.getId(), row.getMessageId(), row.getUserId(), row.getChannel(), row.getTarget(),
        row.getStatus(), row.getContent(), row.getCreateTime());
  }

  private ConsumerRecordView recordView(ConsumerRecordRow row) {
    return new ConsumerRecordView(row.getId(), row.getEventKey(), row.getConsumerGroup(), row.getEventType(),
        row.getStatus(), row.getLastError(), row.getCreateTime(), row.getUpdateTime());
  }

  private void createDeliveries(MessageView message, String channels, String targetPhone) {
    Arrays.stream(channels.split(","))
        .map(String::trim)
        .filter(channel -> !channel.isBlank())
        .distinct()
        .forEach(channel -> notifyMapper.insertDelivery(idGenerator.next("notifyDelivery"), message.messageId(),
            message.userId(), channel, deliveryTarget(channel, message.userId(), targetPhone), "SENT",
            message.content(), LocalDateTime.now()));
  }

  private String deliveryTarget(String channel, long userId, String targetPhone) {
    if ("SMS_MOCK".equals(channel)) {
      return targetPhone == null || targetPhone.isBlank() ? "sms-mock:" + userId : targetPhone;
    }
    return "user:" + userId;
  }

  private String render(String template, Map<String, String> variables) {
    String rendered = template;
    for (Map.Entry<String, String> entry : variables.entrySet()) {
      rendered = rendered.replace("{{" + entry.getKey() + "}}", entry.getValue());
    }
    return rendered;
  }

  private void afterCommitOrNow(Runnable action) {
    if (TransactionSynchronizationManager.isSynchronizationActive()
        && TransactionSynchronizationManager.isActualTransactionActive()) {
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCommit() {
          action.run();
        }
      });
      return;
    }
    action.run();
  }

  private Map<String, Object> fromJson(String payloadJson) {
    try {
      return objectMapper.readValue(payloadJson, EVENT_PAYLOAD);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("failed to deserialize notify event payload", ex);
    }
  }

  private long longNumber(Object value) {
    if (value == null) {
      return 0L;
    }
    if (value instanceof Number number) {
      return number.longValue();
    }
    return Long.parseLong(String.valueOf(value));
  }

  private String content(String eventType, long orderId) {
    return switch (eventType) {
      case "OrderCreated" -> "订单 " + orderId + " 已创建，请及时支付";
      case "OrderPaid" -> "订单 " + orderId + " 已支付，等待商户接单";
      case "OrderCancelled" -> "订单 " + orderId + " 已取消";
      case "OrderMerchantAccepted", "FulfillmentAccepted" -> "订单 " + orderId + " 商户已接单";
      case "OrderMealReady", "FulfillmentMealReady" -> "订单 " + orderId + " 已出餐，等待骑手取餐";
      case "OrderPickedUp", "FulfillmentPickedUp" -> "订单 " + orderId + " 骑手已取餐";
      case "OrderDelivered", "FulfillmentDelivered" -> "订单 " + orderId + " 已送达";
      default -> null;
    };
  }
}
