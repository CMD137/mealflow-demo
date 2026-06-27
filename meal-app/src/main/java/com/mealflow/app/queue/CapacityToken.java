package com.mealflow.app.queue;

import com.mealflow.common.status.CapacityTokenStatus;
import java.time.LocalDateTime;

public class CapacityToken {
  public final long id;
  public final String requestId;
  public final long merchantId;
  public final Long ticketId;
  public Long orderId;
  public CapacityTokenStatus status;
  public final LocalDateTime expireTime;
  public String releaseReason;

  CapacityToken(long id, String requestId, long merchantId, Long ticketId, Long orderId,
      CapacityTokenStatus status, LocalDateTime expireTime, String releaseReason) {
    this.id = id;
    this.requestId = requestId;
    this.merchantId = merchantId;
    this.ticketId = ticketId;
    this.orderId = orderId;
    this.status = status;
    this.expireTime = expireTime;
    this.releaseReason = releaseReason;
  }
}
