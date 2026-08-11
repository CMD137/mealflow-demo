package com.mealflow.order;

import com.mealflow.common.api.Result;
import com.mealflow.common.api.ErrorCode;
import com.mealflow.common.exception.BizException;
import com.mealflow.common.security.RequestIdentity;
import com.mealflow.order.api.AdminOrderQuery;
import com.mealflow.order.api.CancelOrderRequest;
import com.mealflow.order.api.LocalEventView;
import com.mealflow.order.api.OrderStatisticsView;
import com.mealflow.order.api.OrderView;
import com.mealflow.order.api.SubmitOrderRequest;
import com.mealflow.order.api.SubmitOrderResponse;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/orders")
public class OrderController {
  private final OrderService orderService;
  public OrderController(OrderService orderService) {
    this.orderService = orderService;
  }

  @PostMapping("/submit")
  public Result<SubmitOrderResponse> submit(@RequestHeader(value = "X-User-Id", required = false) Long userId,
      @Valid @RequestBody SubmitOrderRequest request) {
    return Result.ok(orderService.submit(RequestIdentity.requireUser(userId), request));
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
  public Result<Void> cancel(@PathVariable long orderId,
      @RequestHeader(value = "X-User-Id", required = false) Long userId,
      @Valid @RequestBody CancelOrderRequest request) {
    requireUserOrder(orderId, RequestIdentity.requireUser(userId));
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
  public Result<OrderView> get(@PathVariable long orderId,
      @RequestHeader(value = "X-User-Id", required = false) Long userId) {
    return Result.ok(requireUserOrder(orderId, RequestIdentity.requireUser(userId)));
  }

  @GetMapping
  public Result<List<OrderView>> list(@RequestHeader(value = "X-User-Id", required = false) Long userId) {
    long currentUserId = RequestIdentity.requireUser(userId);
    return Result.ok(orderService.list().stream().filter(order -> order.userId() == currentUserId).toList());
  }

  @GetMapping("/admin")
  public Result<List<OrderView>> adminOrders(
      @RequestHeader(value = "X-Merchant-Id", required = false) Long merchantId,
      @RequestParam(required = false) Long userId,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime beginTime,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
    return Result.ok(orderService.adminOrders(new AdminOrderQuery(RequestIdentity.requireMerchant(merchantId), userId, status,
        beginTime, endTime)));
  }

  @GetMapping("/admin/statistics")
  public Result<OrderStatisticsView> adminStatistics(
      @RequestHeader(value = "X-Merchant-Id", required = false) Long merchantId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime beginTime,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
    return Result.ok(orderService.adminStatistics(new AdminOrderQuery(RequestIdentity.requireMerchant(merchantId), null, null,
        beginTime, endTime)));
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

  private OrderView requireUserOrder(long orderId, long userId) {
    OrderView order = orderService.get(orderId);
    if (order.userId() != userId) {
      throw new BizException(ErrorCode.FORBIDDEN, "order does not belong to current user");
    }
    return order;
  }
}
