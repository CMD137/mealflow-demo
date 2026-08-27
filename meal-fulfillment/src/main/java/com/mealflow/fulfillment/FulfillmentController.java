package com.mealflow.fulfillment;

import com.mealflow.common.api.Result;
import com.mealflow.common.security.RequestIdentity;
import com.mealflow.fulfillment.api.FulfillmentOperationView;
import com.mealflow.fulfillment.api.FulfillmentRequest;
import com.mealflow.fulfillment.api.LocalEventView;
import com.mealflow.fulfillment.api.OrderView;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/fulfillment/orders")
public class FulfillmentController {
  private final FulfillmentService fulfillmentService;

  public FulfillmentController(FulfillmentService fulfillmentService) {
    this.fulfillmentService = fulfillmentService;
  }

  @PostMapping("/{orderId}/accept")
  public Result<OrderView> accept(@PathVariable long orderId,
      @RequestHeader(value = "X-Merchant-Id", required = false) Long merchantId,
      @Valid @RequestBody FulfillmentRequest request) {
    return Result.ok(fulfillmentService.accept(RequestIdentity.requireMerchant(merchantId), orderId, request.requestId()));
  }

  @PostMapping("/{orderId}/meal-ready")
  public Result<OrderView> mealReady(@PathVariable long orderId,
      @RequestHeader(value = "X-Merchant-Id", required = false) Long merchantId,
      @Valid @RequestBody FulfillmentRequest request) {
    return Result.ok(fulfillmentService.mealReady(RequestIdentity.requireMerchant(merchantId), orderId, request.requestId()));
  }

  @PostMapping("/{orderId}/picked-up")
  public Result<OrderView> pickedUp(@PathVariable long orderId,
      @RequestHeader(value = "X-Merchant-Id", required = false) Long merchantId,
      @Valid @RequestBody FulfillmentRequest request) {
    return Result.ok(fulfillmentService.pickedUp(RequestIdentity.requireMerchant(merchantId), orderId, request.requestId()));
  }

  @PostMapping("/{orderId}/delivered")
  public Result<OrderView> delivered(@PathVariable long orderId,
      @RequestHeader(value = "X-Merchant-Id", required = false) Long merchantId,
      @Valid @RequestBody FulfillmentRequest request) {
    return Result.ok(fulfillmentService.delivered(RequestIdentity.requireMerchant(merchantId), orderId, request.requestId()));
  }

  @GetMapping("/internal/operations")
  public Result<List<FulfillmentOperationView>> operations() {
    return Result.ok(fulfillmentService.operations());
  }

  @GetMapping("/internal/events")
  public Result<List<LocalEventView>> events() {
    return Result.ok(fulfillmentService.events());
  }

  @PostMapping("/internal/events/dispatch")
  public Result<Integer> dispatchEvents() {
    return Result.ok(fulfillmentService.dispatchPendingEvents(100));
  }
}
