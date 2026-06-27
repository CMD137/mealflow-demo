package com.mealflow.app.queue;

import com.mealflow.common.status.QueueTicketStatus;
import java.time.LocalDateTime;

public class QueueTicket {
  public final long id;
  public final String ticketNo;
  public final String requestId;
  public final long userId;
  public final long merchantId;
  public QueueTicketStatus status;
  public final long queueScore;
  public final int aheadCountSnapshot;
  public final int estimatedWaitSeconds;
  public final LocalDateTime expireTime;
  public final QueueTicketSnapshot snapshot;
  public Long orderId;
  public LocalDateTime readyTime;
  public LocalDateTime processingTime;

  QueueTicket(long id, String ticketNo, String requestId, long userId, long merchantId, QueueTicketStatus status,
      long queueScore, int aheadCountSnapshot, int estimatedWaitSeconds, LocalDateTime expireTime,
      QueueTicketSnapshot snapshot) {
    this.id = id;
    this.ticketNo = ticketNo;
    this.requestId = requestId;
    this.userId = userId;
    this.merchantId = merchantId;
    this.status = status;
    this.queueScore = queueScore;
    this.aheadCountSnapshot = aheadCountSnapshot;
    this.estimatedWaitSeconds = estimatedWaitSeconds;
    this.expireTime = expireTime;
    this.snapshot = snapshot;
  }
}
