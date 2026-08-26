package com.mealflow.common.api;

import java.util.List;

/**
 * Uniform page result for admin and internal list endpoints.
 *
 * <p>Introduced so management lists never return unbounded result sets: callers pass
 * {@code page}/{@code pageSize} (pageSize is capped server side) and receive one page plus the
 * total count for paging controls.</p>
 *
 * @param <T> item type
 */
public record PageResult<T>(List<T> items, long total, int page, int pageSize) {

  public static <T> PageResult<T> of(List<T> items, long total, int page, int pageSize) {
    return new PageResult<>(items, total, page, pageSize);
  }
}
