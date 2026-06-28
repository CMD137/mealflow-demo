package com.mealflow.notify;

import com.mealflow.common.api.Result;
import com.mealflow.notify.api.ConsumedPushMessageRequest;
import com.mealflow.notify.api.ConsumerRecordView;
import com.mealflow.notify.api.MessageView;
import com.mealflow.notify.api.PushMessageRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/notify")
public class NotifyController {
  private final NotifyService notifyService;
  private final long defaultUserId;

  public NotifyController(NotifyService notifyService, @Value("${mealflow.demo.default-user-id:100}") long defaultUserId) {
    this.notifyService = notifyService;
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

  @GetMapping("/messages")
  public Result<List<MessageView>> list(@RequestHeader(value = "X-User-Id", required = false) Long userId) {
    return Result.ok(notifyService.list(userId == null ? defaultUserId : userId));
  }

  @GetMapping("/internal/consumer-records")
  public Result<List<ConsumerRecordView>> consumerRecords() {
    return Result.ok(notifyService.consumerRecords());
  }
}
