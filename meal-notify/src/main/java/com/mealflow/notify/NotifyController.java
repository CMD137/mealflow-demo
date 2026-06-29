package com.mealflow.notify;

import com.mealflow.common.api.Result;
import com.mealflow.notify.api.ConsumedPushMessageRequest;
import com.mealflow.notify.api.ConsumerRecordView;
import com.mealflow.notify.api.DeliveryView;
import com.mealflow.notify.api.MessageView;
import com.mealflow.notify.api.PushMessageRequest;
import com.mealflow.notify.api.TemplateMessageRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/notify")
public class NotifyController {
  private final NotifyService notifyService;
  private final NotifyStreamService notifyStreamService;
  private final long defaultUserId;

  public NotifyController(NotifyService notifyService, NotifyStreamService notifyStreamService,
      @Value("${mealflow.demo.default-user-id:100}") long defaultUserId) {
    this.notifyService = notifyService;
    this.notifyStreamService = notifyStreamService;
    this.defaultUserId = defaultUserId;
  }

  @PostMapping("/internal/messages")
  public Result<MessageView> push(@Valid @RequestBody PushMessageRequest request) {
    return Result.ok(notifyService.push(request));
  }

  @PostMapping("/internal/events/messages")
  public Result<MessageView> pushFromEvent(@Valid @RequestBody ConsumedPushMessageRequest request) {
    return Result.ok(notifyService.pushOnce(request.eventKey(), request.consumerGroup(), request.message()));
  }

  @PostMapping("/internal/templates/{templateCode}/messages")
  public Result<MessageView> pushTemplate(@PathVariable String templateCode,
      @Valid @RequestBody TemplateMessageRequest request) {
    return Result.ok(notifyService.pushTemplated(templateCode, request));
  }

  @GetMapping("/messages")
  public Result<List<MessageView>> list(@RequestHeader(value = "X-User-Id", required = false) Long userId) {
    return Result.ok(notifyService.list(userId == null ? defaultUserId : userId));
  }

  @GetMapping("/deliveries")
  public Result<List<DeliveryView>> deliveries(@RequestHeader(value = "X-User-Id", required = false) Long userId) {
    return Result.ok(notifyService.deliveries(userId == null ? defaultUserId : userId));
  }

  @GetMapping(value = "/messages/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter stream(@RequestHeader(value = "X-User-Id", required = false) Long userId) {
    return notifyStreamService.subscribe(userId == null ? defaultUserId : userId);
  }

  @GetMapping("/internal/consumer-records")
  public Result<List<ConsumerRecordView>> consumerRecords() {
    return Result.ok(notifyService.consumerRecords());
  }

  @PostMapping("/internal/consumer-records/recover")
  public Result<Integer> recoverConsumerRecords() {
    return Result.ok(notifyService.recoverTimedOutConsumerRecords());
  }
}
