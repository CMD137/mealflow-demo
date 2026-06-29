package com.mealflow.order;

import com.mealflow.common.api.Result;
import com.mealflow.order.api.CancelOrderRequest;
import com.mealflow.order.api.LocalEventView;
import com.mealflow.order.api.OrderView;
import com.mealflow.order.api.SubmitOrderRequest;
import com.mealflow.order.api.SubmitOrderResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/orders")
public class OrderController {
  private final OrderService orderService;
  private final long defaultUserId;

  public OrderController(OrderService orderService, @Value("${mealflow.demo.default-user-id:100}") long defaultUserId) {
    this.orderService = orderService;
    this.defaultUserId = defaultUserId;
  }

  @PostMapping("/submit")
  public Result<SubmitOrderResponse> submit(@RequestHeader(value = "X-User-Id", required = false) Long userId,
      @Valid @RequestBody SubmitOrderRequest request) {
    return Result.ok(orderService.submit(userId == null ? defaultUserId : userId, request));
  }

  @PostMapping("/internal/from-ticket/{ticketId}/{capacityTokenId}")
  public Result<OrderView> fromTicket(@PathVariable long ticketId, @PathVariable long capacityTokenId) {
    return Result.ok(orderService.get(orderService.createOrderFromTicket(ticketId, capacityTokenId).id));
  }

  @PostMapping("/{orderId}/pay-success")
  public Result<Void> paySuccess(@PathVariable long orderId) {
    orderService.markPaid(orderId);
    return Result.ok();
  }

  @PostMapping("/{orderId}/cancel")
  public Result<Void> cancel(@PathVariable long orderId, @Valid @RequestBody CancelOrderRequest request) {
    orderService.cancel(orderId, request.reason());
    return Result.ok();
  }

  @PostMapping("/{orderId}/merchant-accept")
  public Result<OrderView> merchantAccept(@PathVariable long orderId) {
    orderService.merchantAccept(orderId);
    return Result.ok(orderService.get(orderId));
  }

  @PostMapping("/{orderId}/meal-ready")
  public Result<OrderView> mealReady(@PathVariable long orderId) {
    orderService.mealReady(orderId);
    return Result.ok(orderService.get(orderId));
  }

  @PostMapping("/{orderId}/picked-up")
  public Result<OrderView> pickedUp(@PathVariable long orderId) {
    orderService.pickedUp(orderId);
    return Result.ok(orderService.get(orderId));
  }

  @PostMapping("/{orderId}/delivered")
  public Result<OrderView> delivered(@PathVariable long orderId) {
    orderService.delivered(orderId);
    return Result.ok(orderService.get(orderId));
  }

  @GetMapping("/{orderId}")
  public Result<OrderView> get(@PathVariable long orderId) {
    return Result.ok(orderService.get(orderId));
  }

  @GetMapping
  public Result<List<OrderView>> list() {
    return Result.ok(orderService.list());
  }

  @GetMapping("/internal/events")
  public Result<List<LocalEventView>> events() {
    return Result.ok(orderService.events());
  }

  @PostMapping("/internal/events/dispatch")
  public Result<Integer> dispatchEvents() {
    return Result.ok(orderService.dispatchPendingEvents(100));
  }

  @PostMapping("/internal/consumer-records/recover")
  public Result<Integer> recoverConsumerRecords() {
    return Result.ok(orderService.recoverTimedOutConsumerRecords());
  }

  @PostMapping("/internal/consumer-records/{eventKey}/groups/{consumerGroup}/replay")
  public Result<Boolean> replayConsumerRecord(@PathVariable String eventKey, @PathVariable String consumerGroup) {
    return Result.ok(orderService.replayPaymentConsumerRecord(eventKey, consumerGroup));
  }
}
