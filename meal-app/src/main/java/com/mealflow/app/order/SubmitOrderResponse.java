package com.mealflow.app.order;

import java.time.LocalDateTime;

public record SubmitOrderResponse(
    String mode,
    Long orderId,
    Long payOrderId,
    String status,
    Long ticketId,
    String ticketNo,
    int aheadCount,
    int estimatedWaitSeconds,
    LocalDateTime expireTime
) {
  static SubmitOrderResponse orderCreated(long orderId, long payOrderId, String status) {
    return new SubmitOrderResponse("ORDER_CREATED", orderId, payOrderId, status, null, null, 0, 0, null);
  }

  static SubmitOrderResponse queued(long ticketId, String ticketNo, int aheadCount, int waitSeconds,
      LocalDateTime expireTime) {
    return new SubmitOrderResponse("QUEUED", null, null, null, ticketId, ticketNo, aheadCount, waitSeconds,
        expireTime);
  }
}
