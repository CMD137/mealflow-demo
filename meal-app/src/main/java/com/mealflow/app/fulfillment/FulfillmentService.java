package com.mealflow.app.fulfillment;

import com.mealflow.app.order.OrderService;
import com.mealflow.app.order.OrderView;
import com.mealflow.app.queue.QueueService;
import com.mealflow.common.api.ErrorCode;
import com.mealflow.common.exception.BizException;
import com.mealflow.infra.idempotent.IdempotentTemplate;
import org.springframework.stereotype.Service;

@Service
public class FulfillmentService {
  private final OrderService orderService;
  private final QueueService queueService;
  private final IdempotentTemplate idempotentTemplate;

  public FulfillmentService(OrderService orderService, QueueService queueService, IdempotentTemplate idempotentTemplate) {
    this.orderService = orderService;
    this.queueService = queueService;
    this.idempotentTemplate = idempotentTemplate;
  }

  public OrderView accept(long orderId, String requestId) {
    return idempotentTemplate.execute("fulfillment-accept:" + requestId, () -> {
      orderService.merchantAccept(orderId);
      return orderService.get(orderId);
    });
  }

  public OrderView mealReady(long orderId, String requestId) {
    return idempotentTemplate.execute("fulfillment-meal-ready:" + requestId, () -> {
      orderService.mealReady(orderId);
      OrderView order = orderService.get(orderId);
      queueService.releaseCapacity(order.capacityTokenId(), "MEAL_READY");
      return orderService.get(orderId);
    });
  }

  public OrderView pickedUp(long orderId, String requestId) {
    return idempotentTemplate.execute("fulfillment-picked-up:" + requestId, () -> {
      orderService.pickedUp(orderId);
      return orderService.get(orderId);
    });
  }

  public OrderView delivered(long orderId, String requestId) {
    return idempotentTemplate.execute("fulfillment-delivered:" + requestId, () -> {
      orderService.delivered(orderId);
      return orderService.get(orderId);
    });
  }

  public OrderView reject(long orderId, String requestId, String reason) {
    return idempotentTemplate.execute("fulfillment-reject:" + requestId, () -> {
      throw new BizException(ErrorCode.BAD_REQUEST, "MVP 暂未开放拒单，建议走订单取消演示资源释放");
    });
  }
}
