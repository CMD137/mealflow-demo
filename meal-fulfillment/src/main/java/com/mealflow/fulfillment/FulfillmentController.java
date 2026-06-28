package com.mealflow.fulfillment;

import com.mealflow.common.api.Result;
import com.mealflow.fulfillment.api.FulfillmentOperationView;
import com.mealflow.fulfillment.api.FulfillmentRequest;
import com.mealflow.fulfillment.api.OrderView;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
  public Result<OrderView> accept(@PathVariable long orderId, @Valid @RequestBody FulfillmentRequest request) {
    return Result.ok(fulfillmentService.accept(orderId, request.requestId()));
  }

  @PostMapping("/{orderId}/meal-ready")
  public Result<OrderView> mealReady(@PathVariable long orderId, @Valid @RequestBody FulfillmentRequest request) {
    return Result.ok(fulfillmentService.mealReady(orderId, request.requestId()));
  }

  @PostMapping("/{orderId}/picked-up")
  public Result<OrderView> pickedUp(@PathVariable long orderId, @Valid @RequestBody FulfillmentRequest request) {
    return Result.ok(fulfillmentService.pickedUp(orderId, request.requestId()));
  }

  @PostMapping("/{orderId}/delivered")
  public Result<OrderView> delivered(@PathVariable long orderId, @Valid @RequestBody FulfillmentRequest request) {
    return Result.ok(fulfillmentService.delivered(orderId, request.requestId()));
  }

  @GetMapping("/internal/operations")
  public Result<List<FulfillmentOperationView>> operations() {
    return Result.ok(fulfillmentService.operations());
  }
}
