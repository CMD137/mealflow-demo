package com.mealflow.queue;

import com.mealflow.common.api.Result;
import com.mealflow.queue.api.BindOrderRequest;
import com.mealflow.queue.api.CapacityTokenView;
import com.mealflow.queue.api.QueueApplyRequest;
import com.mealflow.queue.api.QueueApplyResponse;
import com.mealflow.queue.api.QueueTicketSnapshot;
import com.mealflow.queue.api.QueueTicketView;
import com.mealflow.queue.api.ReleaseCapacityRequest;
import com.mealflow.queue.api.ReleaseCapacityResponse;
import com.mealflow.queue.api.SetMerchantLimitRequest;
import jakarta.validation.Valid;
import java.util.List;
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

  @PostMapping("/internal/capacity/apply")
  public Result<QueueApplyResponse> apply(@Valid @RequestBody QueueApplyRequest request) {
    return Result.ok(queueService.apply(request));
  }

  @PostMapping("/internal/capacity/{capacityTokenId}/release")
  public Result<ReleaseCapacityResponse> release(@PathVariable long capacityTokenId,
      @Valid @RequestBody ReleaseCapacityRequest request) {
    return Result.ok(queueService.releaseCapacity(capacityTokenId, request.reason()));
  }

  @PostMapping("/internal/capacity/{capacityTokenId}/bind-order")
  public Result<Void> bindOrder(@PathVariable long capacityTokenId, @Valid @RequestBody BindOrderRequest request) {
    queueService.bindTokenOrder(capacityTokenId, request.orderId());
    return Result.ok();
  }

  @PostMapping("/internal/tickets/{ticketId}/processing")
  public Result<QueueTicketSnapshot> markProcessing(@PathVariable long ticketId) {
    return Result.ok(queueService.markProcessing(ticketId));
  }

  @PostMapping("/internal/tickets/{ticketId}/order-created")
  public Result<Void> orderCreated(@PathVariable long ticketId, @Valid @RequestBody BindOrderRequest request) {
    queueService.confirmOrderCreated(ticketId, request.orderId());
    return Result.ok();
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

  @GetMapping("/internal/tickets")
  public Result<List<QueueTicketView>> tickets() {
    return Result.ok(queueService.tickets());
  }

  @GetMapping("/internal/capacity/tokens")
  public Result<List<CapacityTokenView>> tokens() {
    return Result.ok(queueService.tokens());
  }
}
