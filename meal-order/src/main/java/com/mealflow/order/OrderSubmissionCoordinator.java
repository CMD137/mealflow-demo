package com.mealflow.order;

import com.mealflow.order.api.SubmitOrderRequest;
import com.mealflow.order.api.SubmitOrderResponse;
import org.springframework.stereotype.Service;

/**
 * Coordinates the persistent request record outside the order transaction.
 * A successful response is stored only after {@link OrderService#submitInTransaction}
 * has returned from its transactional proxy, which means the order write has committed.
 */
@Service
public class OrderSubmissionCoordinator {
  private final OrderIdempotencyService idempotencyService;
  private final OrderService orderService;

  public OrderSubmissionCoordinator(OrderIdempotencyService idempotencyService, OrderService orderService) {
    this.idempotencyService = idempotencyService;
    this.orderService = orderService;
  }

  public SubmitOrderResponse submit(long userId, SubmitOrderRequest request) {
    return idempotencyService.execute(userId, request.requestId(), request,
        () -> orderService.submitInTransaction(userId, request));
  }
}
