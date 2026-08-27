package com.mealflow.order.api;

import java.time.LocalDateTime;

public record AdminOrderQuery(Long merchantId, Long userId, String status, LocalDateTime beginTime,
    LocalDateTime endTime, int page, int pageSize) {

  public AdminOrderQuery {
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
