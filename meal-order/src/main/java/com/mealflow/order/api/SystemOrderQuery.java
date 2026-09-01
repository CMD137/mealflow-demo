package com.mealflow.order.api;

import java.time.LocalDateTime;

/** Explicit system-governance query. It deliberately has no mutation or merchant ownership field. */
public record SystemOrderQuery(Long merchantId, Long userId, String status, LocalDateTime from, LocalDateTime to,
    int page, int pageSize) {

  public SystemOrderQuery {
    if (page < 1) {
      page = 1;
    }
    if (pageSize < 1 || pageSize > 100) {
      pageSize = 20;
    }
  }

  public int offset() {
    return (page - 1) * pageSize;
  }
}
