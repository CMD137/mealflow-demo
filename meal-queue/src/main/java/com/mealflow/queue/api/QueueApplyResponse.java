package com.mealflow.queue.api;

import java.time.LocalDateTime;

public record QueueApplyResponse(
    String result,
    Long capacityTokenId,
    Long ticketId,
    String ticketNo,
    int aheadCount,
    int estimatedWaitSeconds,
    LocalDateTime expireTime
) {
  public static QueueApplyResponse ready(long capacityTokenId) {
    return new QueueApplyResponse("READY", capacityTokenId, null, null, 0, 0, null);
  }

  public static QueueApplyResponse queued(long ticketId, String ticketNo, int aheadCount, int waitSeconds,
      LocalDateTime expireTime) {
    return new QueueApplyResponse("QUEUED", null, ticketId, ticketNo, aheadCount, waitSeconds, expireTime);
  }
}
