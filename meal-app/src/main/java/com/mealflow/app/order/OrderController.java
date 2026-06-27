package com.mealflow.app.order;

import com.mealflow.common.api.Result;
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

  public OrderController(OrderService orderService, @Value("${mealflow.demo.default-user-id}") long defaultUserId) {
    this.orderService = orderService;
    this.defaultUserId = defaultUserId;
  }

  @PostMapping("/submit")
  public Result<SubmitOrderResponse> submit(@RequestHeader(value = "X-User-Id", required = false) Long userId,
      @Valid @RequestBody SubmitOrderRequest request) {
    return Result.ok(orderService.submit(userId == null ? defaultUserId : userId, request));
  }

  @GetMapping("/{orderId}")
  public Result<OrderView> get(@PathVariable long orderId) {
    return Result.ok(orderService.get(orderId));
  }

  @GetMapping
  public Result<List<OrderView>> list() {
    return Result.ok(orderService.orders());
  }

  @PostMapping("/{orderId}/cancel")
  public Result<Void> cancel(@PathVariable long orderId, @Valid @RequestBody CancelOrderRequest request) {
    orderService.cancel(orderId, request.reason());
    return Result.ok();
  }
}
