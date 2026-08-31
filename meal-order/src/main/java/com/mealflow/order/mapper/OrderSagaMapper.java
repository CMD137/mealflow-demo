package com.mealflow.order.mapper;

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
public interface OrderSagaMapper {
  @Insert("""
      INSERT INTO order_saga_step (
        id, order_id, pay_order_id, saga_type, step_name, step_order, reason, status,
        retry_count, next_retry_time, lease_until, last_error, promoted_ticket_id, promoted_capacity_token_id,
        create_time, update_time
      ) VALUES (
        #{id}, #{orderId}, #{payOrderId}, #{sagaType}, #{stepName}, #{stepOrder}, #{reason},
        'NEW', 0, NULL, NULL, NULL, NULL, NULL, #{now}, #{now}
      )
      ON DUPLICATE KEY UPDATE id = id
      """)
  int insertStep(@Param("id") long id, @Param("orderId") long orderId,
      @Param("payOrderId") long payOrderId, @Param("sagaType") String sagaType,
      @Param("stepName") String stepName, @Param("stepOrder") int stepOrder,
      @Param("reason") String reason, @Param("now") LocalDateTime now);

  @Select("""
      SELECT s.id, s.order_id, s.pay_order_id, s.saga_type, s.step_name, s.step_order,
             s.reason, s.status, s.retry_count, s.next_retry_time, s.lease_until, s.last_error,
             s.promoted_ticket_id, s.promoted_capacity_token_id
      FROM order_saga_step s
      WHERE s.status IN ('NEW', 'FAILED')
        AND (s.next_retry_time IS NULL OR s.next_retry_time <= #{now})
        AND NOT EXISTS (
          SELECT 1 FROM order_saga_step previous
          WHERE previous.order_id = s.order_id AND previous.saga_type = s.saga_type
            AND previous.step_order < s.step_order AND previous.status <> 'SUCCESS'
        )
      ORDER BY s.id
      LIMIT #{limit}
      """)
  @Results(id = "orderSagaStepMap", value = {
      @Result(column = "id", property = "id"),
      @Result(column = "order_id", property = "orderId"),
      @Result(column = "pay_order_id", property = "payOrderId"),
      @Result(column = "saga_type", property = "sagaType"),
      @Result(column = "step_name", property = "stepName"),
      @Result(column = "step_order", property = "stepOrder"),
      @Result(column = "reason", property = "reason"),
      @Result(column = "status", property = "status"),
      @Result(column = "retry_count", property = "retryCount"),
      @Result(column = "next_retry_time", property = "nextRetryTime"),
      @Result(column = "lease_until", property = "leaseUntil"),
      @Result(column = "last_error", property = "lastError"),
      @Result(column = "promoted_ticket_id", property = "promotedTicketId"),
      @Result(column = "promoted_capacity_token_id", property = "promotedCapacityTokenId")
  })
  List<OrderSagaStepRow> findReady(@Param("now") LocalDateTime now, @Param("limit") int limit);

  @Select("""
      SELECT s.id, s.order_id, s.pay_order_id, s.saga_type, s.step_name, s.step_order,
             s.reason, s.status, s.retry_count, s.next_retry_time, s.lease_until, s.last_error,
             s.promoted_ticket_id, s.promoted_capacity_token_id
      FROM order_saga_step s
      WHERE s.order_id = #{orderId} AND s.status IN ('NEW', 'FAILED')
        AND (s.next_retry_time IS NULL OR s.next_retry_time <= #{now})
        AND NOT EXISTS (
          SELECT 1 FROM order_saga_step previous
          WHERE previous.order_id = s.order_id AND previous.saga_type = s.saga_type
            AND previous.step_order < s.step_order AND previous.status <> 'SUCCESS'
        )
      ORDER BY s.id
      LIMIT 1
      """)
  @Results(id = "orderSagaStepForOrderMap", value = {
      @Result(column = "id", property = "id"),
      @Result(column = "order_id", property = "orderId"),
      @Result(column = "pay_order_id", property = "payOrderId"),
      @Result(column = "saga_type", property = "sagaType"),
      @Result(column = "step_name", property = "stepName"),
      @Result(column = "step_order", property = "stepOrder"),
      @Result(column = "reason", property = "reason"),
      @Result(column = "status", property = "status"),
      @Result(column = "retry_count", property = "retryCount"),
      @Result(column = "next_retry_time", property = "nextRetryTime"),
      @Result(column = "lease_until", property = "leaseUntil"),
      @Result(column = "last_error", property = "lastError"),
      @Result(column = "promoted_ticket_id", property = "promotedTicketId"),
      @Result(column = "promoted_capacity_token_id", property = "promotedCapacityTokenId")
  })
  OrderSagaStepRow findReadyForOrder(@Param("orderId") long orderId, @Param("now") LocalDateTime now);

  @Update("""
      UPDATE order_saga_step
      SET status = 'PROCESSING', retry_count = retry_count + 1, lease_until = #{leaseUntil},
          last_error = NULL, update_time = #{now}
      WHERE id = #{id} AND status IN ('NEW', 'FAILED')
        AND (next_retry_time IS NULL OR next_retry_time <= #{now})
      """)
  int markProcessing(@Param("id") long id, @Param("now") LocalDateTime now,
      @Param("leaseUntil") LocalDateTime leaseUntil);

  @Update("""
      UPDATE order_saga_step
      SET status = 'SUCCESS', next_retry_time = NULL, lease_until = NULL, last_error = NULL, update_time = #{now}
      WHERE id = #{id} AND status = 'PROCESSING'
      """)
  int markSuccess(@Param("id") long id, @Param("now") LocalDateTime now);

  @Update("""
      UPDATE order_saga_step
      SET status = 'SUCCESS', next_retry_time = NULL, lease_until = NULL,
          last_error = 'PAYMENT_ALREADY_PAID', update_time = #{now}
      WHERE order_id = #{orderId} AND saga_type = #{sagaType} AND step_order > #{stepOrder}
        AND status IN ('NEW', 'FAILED')
      """)
  int skipRemainingAfterPayment(long orderId, String sagaType, int stepOrder, LocalDateTime now);

  @Update("""
      UPDATE order_saga_step
      SET status = 'FAILED', next_retry_time = #{nextRetryTime}, lease_until = NULL,
          last_error = #{lastError}, update_time = #{now}
      WHERE id = #{id} AND status = 'PROCESSING'
      """)
  int markFailed(@Param("id") long id, @Param("lastError") String lastError,
      @Param("nextRetryTime") LocalDateTime nextRetryTime, @Param("now") LocalDateTime now);

  @Update("""
      UPDATE order_saga_step
      SET promoted_ticket_id = #{ticketId}, promoted_capacity_token_id = #{capacityTokenId}, update_time = #{now}
      WHERE id = #{id} AND status = 'PROCESSING'
      """)
  int savePromotionResult(@Param("id") long id, @Param("ticketId") Long ticketId,
      @Param("capacityTokenId") Long capacityTokenId, @Param("now") LocalDateTime now);

  @Select("""
      SELECT id, order_id, pay_order_id, saga_type, step_name, step_order, reason, status,
             retry_count, next_retry_time, lease_until, last_error, promoted_ticket_id, promoted_capacity_token_id
      FROM order_saga_step
      WHERE order_id = #{orderId} AND saga_type = #{sagaType} AND step_name = 'RELEASE_CAPACITY'
      """)
  @Results(id = "promotionResultMap", value = {
      @Result(column = "id", property = "id"), @Result(column = "order_id", property = "orderId"),
      @Result(column = "pay_order_id", property = "payOrderId"), @Result(column = "saga_type", property = "sagaType"),
      @Result(column = "step_name", property = "stepName"), @Result(column = "step_order", property = "stepOrder"),
      @Result(column = "reason", property = "reason"), @Result(column = "status", property = "status"),
      @Result(column = "retry_count", property = "retryCount"), @Result(column = "next_retry_time", property = "nextRetryTime"),
      @Result(column = "lease_until", property = "leaseUntil"), @Result(column = "last_error", property = "lastError"),
      @Result(column = "promoted_ticket_id", property = "promotedTicketId"),
      @Result(column = "promoted_capacity_token_id", property = "promotedCapacityTokenId")
  })
  OrderSagaStepRow findPromotionResult(@Param("orderId") long orderId, @Param("sagaType") String sagaType);

  @Update("""
      UPDATE order_saga_step
      SET status = 'FAILED', next_retry_time = #{now}, lease_until = NULL,
          last_error = 'PROCESSING_TIMEOUT', update_time = #{now}
      WHERE status = 'PROCESSING' AND lease_until < #{now}
      """)
  int recoverExpired(@Param("now") LocalDateTime now);

  @Select("SELECT COUNT(*) FROM order_saga_step WHERE order_id = #{orderId} AND saga_type = #{sagaType} AND status <> 'SUCCESS'")
  int countIncomplete(@Param("orderId") long orderId, @Param("sagaType") String sagaType);

  @Select("SELECT COUNT(*) FROM order_saga_step WHERE order_id = #{orderId} AND saga_type = #{sagaType} AND step_name = #{stepName} AND status = 'SUCCESS'")
  int countSuccessfulStep(@Param("orderId") long orderId, @Param("sagaType") String sagaType,
      @Param("stepName") String stepName);

  @Select("""
      SELECT id, order_id, pay_order_id, saga_type, step_name, step_order, reason, status,
             retry_count, next_retry_time, lease_until, last_error, promoted_ticket_id, promoted_capacity_token_id
      FROM order_saga_step WHERE order_id = #{orderId} ORDER BY id
      """)
  @Results(id = "orderSagaStepsByOrderMap", value = {
      @Result(column = "id", property = "id"), @Result(column = "order_id", property = "orderId"),
      @Result(column = "pay_order_id", property = "payOrderId"),
      @Result(column = "saga_type", property = "sagaType"),
      @Result(column = "step_name", property = "stepName"),
      @Result(column = "step_order", property = "stepOrder"), @Result(column = "reason", property = "reason"),
      @Result(column = "status", property = "status"),
      @Result(column = "retry_count", property = "retryCount"),
      @Result(column = "next_retry_time", property = "nextRetryTime"),
      @Result(column = "lease_until", property = "leaseUntil"),
      @Result(column = "last_error", property = "lastError"),
      @Result(column = "promoted_ticket_id", property = "promotedTicketId"),
      @Result(column = "promoted_capacity_token_id", property = "promotedCapacityTokenId")
  })
  List<OrderSagaStepRow> findByOrderId(long orderId);

  @Update("UPDATE order_saga_step SET next_retry_time = #{now} WHERE order_id = #{orderId} AND status = 'FAILED'")
  int retryNow(@Param("orderId") long orderId, @Param("now") LocalDateTime now);
}
