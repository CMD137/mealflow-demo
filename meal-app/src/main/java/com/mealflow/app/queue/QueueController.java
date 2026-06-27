package com.mealflow.app.queue;

import com.mealflow.common.api.Result;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/queue")
public class QueueController {
  private final QueueService queueService;

  public QueueController(QueueService queueService) {
    this.queueService = queueService;
  }

  @GetMapping("/tickets/{ticketId}")
  public Result<QueueTicketView> getTicket(@PathVariable long ticketId) {
    return Result.ok(queueService.getTicket(ticketId));
  }

  @PostMapping("/tickets/{ticketId}/cancel")
  public Result<Void> cancel(@PathVariable long ticketId) {
    queueService.cancelTicket(ticketId);
    return Result.ok();
  }

  @PostMapping("/merchants/{merchantId}/limit")
  public Result<Void> setLimit(@PathVariable long merchantId, @Valid @RequestBody SetMerchantLimitRequest request) {
    queueService.setMerchantLimit(merchantId, request.limit());
    return Result.ok();
  }

  @GetMapping("/merchants/{merchantId}/metrics")
  public Result<Map<String, Object>> metrics(@PathVariable long merchantId) {
    return Result.ok(queueService.metrics(merchantId));
  }
}
