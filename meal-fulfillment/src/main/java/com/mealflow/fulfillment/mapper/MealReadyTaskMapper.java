package com.mealflow.fulfillment.mapper;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface MealReadyTaskMapper {
  @Insert("""
      INSERT INTO fulfillment_meal_ready_task (
        request_id, order_id, capacity_token_id, order_json, release_done, ready_ticket_id,
        ready_capacity_token_id, promote_done, status, retry_count, next_retry_time, lease_until,
        last_error, create_time, update_time
      ) VALUES (
        #{requestId}, #{orderId}, #{capacityTokenId}, #{orderJson}, FALSE, NULL, NULL, FALSE,
        'NEW', 0, NULL, NULL, NULL, #{now}, #{now}
      )
      ON DUPLICATE KEY UPDATE request_id = request_id
      """)
  int insert(@Param("requestId") String requestId, @Param("orderId") long orderId,
      @Param("capacityTokenId") long capacityTokenId, @Param("orderJson") String orderJson,
      @Param("now") LocalDateTime now);

  @Select("""
      SELECT request_id, order_id, capacity_token_id, order_json, release_done, ready_ticket_id,
             ready_capacity_token_id, promote_done, status, retry_count
      FROM fulfillment_meal_ready_task
      WHERE status IN ('NEW', 'FAILED') AND (next_retry_time IS NULL OR next_retry_time <= #{now})
      ORDER BY create_time
      LIMIT #{limit}
      """)
  @Results(id = "mealReadyTaskMap", value = {
      @Result(column = "request_id", property = "requestId"),
      @Result(column = "order_id", property = "orderId"),
      @Result(column = "capacity_token_id", property = "capacityTokenId"),
      @Result(column = "order_json", property = "orderJson"),
      @Result(column = "release_done", property = "releaseDone"),
      @Result(column = "ready_ticket_id", property = "readyTicketId"),
      @Result(column = "ready_capacity_token_id", property = "readyCapacityTokenId"),
      @Result(column = "promote_done", property = "promoteDone"),
      @Result(column = "status", property = "status"),
      @Result(column = "retry_count", property = "retryCount")
  })
  List<MealReadyTaskRow> findReady(@Param("now") LocalDateTime now, @Param("limit") int limit);

  @Select("""
      SELECT request_id, order_id, capacity_token_id, order_json, release_done, ready_ticket_id,
             ready_capacity_token_id, promote_done, status, retry_count
      FROM fulfillment_meal_ready_task WHERE request_id = #{requestId}
      """)
  @Results(id = "mealReadyTaskByIdMap", value = {
      @Result(column = "request_id", property = "requestId"),
      @Result(column = "order_id", property = "orderId"),
      @Result(column = "capacity_token_id", property = "capacityTokenId"),
      @Result(column = "order_json", property = "orderJson"),
      @Result(column = "release_done", property = "releaseDone"),
      @Result(column = "ready_ticket_id", property = "readyTicketId"),
      @Result(column = "ready_capacity_token_id", property = "readyCapacityTokenId"),
      @Result(column = "promote_done", property = "promoteDone"),
      @Result(column = "status", property = "status"),
      @Result(column = "retry_count", property = "retryCount")
  })
  MealReadyTaskRow findByRequestId(String requestId);

  @Update("""
      UPDATE fulfillment_meal_ready_task
      SET status = 'PROCESSING', retry_count = retry_count + 1, lease_until = #{leaseUntil},
          last_error = NULL, update_time = #{now}
      WHERE request_id = #{requestId} AND status IN ('NEW', 'FAILED')
        AND (next_retry_time IS NULL OR next_retry_time <= #{now})
      """)
  int markProcessing(@Param("requestId") String requestId, @Param("now") LocalDateTime now,
      @Param("leaseUntil") LocalDateTime leaseUntil);

  @Update("""
      UPDATE fulfillment_meal_ready_task
      SET release_done = TRUE, ready_ticket_id = #{ticketId},
          ready_capacity_token_id = #{readyCapacityTokenId}, update_time = #{now}
      WHERE request_id = #{requestId} AND status = 'PROCESSING'
      """)
  int markReleased(@Param("requestId") String requestId, @Param("ticketId") Long ticketId,
      @Param("readyCapacityTokenId") Long readyCapacityTokenId, @Param("now") LocalDateTime now);

  @Update("""
      UPDATE fulfillment_meal_ready_task SET promote_done = TRUE, update_time = #{now}
      WHERE request_id = #{requestId} AND status = 'PROCESSING'
      """)
  int markPromoted(@Param("requestId") String requestId, @Param("now") LocalDateTime now);

  @Update("""
      UPDATE fulfillment_meal_ready_task
      SET status = 'SUCCESS', lease_until = NULL, next_retry_time = NULL, last_error = NULL, update_time = #{now}
      WHERE request_id = #{requestId} AND status = 'PROCESSING'
      """)
  int markSuccess(@Param("requestId") String requestId, @Param("now") LocalDateTime now);

  @Update("""
      UPDATE fulfillment_meal_ready_task
      SET status = 'FAILED', lease_until = NULL, next_retry_time = #{nextRetryTime},
          last_error = #{lastError}, update_time = #{now}
      WHERE request_id = #{requestId} AND status = 'PROCESSING'
      """)
  int markFailed(@Param("requestId") String requestId, @Param("lastError") String lastError,
      @Param("nextRetryTime") LocalDateTime nextRetryTime, @Param("now") LocalDateTime now);

  @Update("""
      UPDATE fulfillment_meal_ready_task
      SET status = 'FAILED', lease_until = NULL, next_retry_time = #{now},
          last_error = 'PROCESSING_TIMEOUT', update_time = #{now}
      WHERE status = 'PROCESSING' AND lease_until < #{now}
      """)
  int recoverExpired(@Param("now") LocalDateTime now);

  @Update("UPDATE fulfillment_meal_ready_task SET next_retry_time = #{now} WHERE request_id = #{requestId} AND status = 'FAILED'")
  int retryNow(@Param("requestId") String requestId, @Param("now") LocalDateTime now);
}
