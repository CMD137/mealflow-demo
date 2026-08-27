package com.mealflow.payment.mapper;

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
public interface PaymentRefundMapper {
  @Insert("""
      INSERT INTO payment_refund (
        id, pay_order_id, provider, merchant_order_no, refund_request_no, amount_cent, status,
        retry_count, next_query_time, create_time, update_time
      ) VALUES (
        #{id}, #{payOrderId}, #{provider}, #{merchantOrderNo}, #{refundRequestNo}, #{amountCent},
        'PROCESSING', 0, #{now}, #{now}, #{now}
      )
      ON DUPLICATE KEY UPDATE id = id
      """)
  int insert(@Param("id") long id, @Param("payOrderId") long payOrderId, @Param("provider") String provider,
      @Param("merchantOrderNo") String merchantOrderNo, @Param("refundRequestNo") String refundRequestNo,
      @Param("amountCent") int amountCent, @Param("now") LocalDateTime now);

  @Select("""
      SELECT id, pay_order_id, provider, merchant_order_no, refund_request_no, amount_cent, status, retry_count
      FROM payment_refund WHERE pay_order_id = #{payOrderId}
      """)
  @Results(id = "paymentRefundMap", value = {
      @Result(column = "id", property = "id"),
      @Result(column = "pay_order_id", property = "payOrderId"),
      @Result(column = "provider", property = "provider"),
      @Result(column = "merchant_order_no", property = "merchantOrderNo"),
      @Result(column = "refund_request_no", property = "refundRequestNo"),
      @Result(column = "amount_cent", property = "amountCent"),
      @Result(column = "status", property = "status"),
      @Result(column = "retry_count", property = "retryCount")
  })
  PaymentRefundRow findByPayOrderId(long payOrderId);

  @Select("""
      SELECT id, pay_order_id, provider, merchant_order_no, refund_request_no, amount_cent, status, retry_count
      FROM payment_refund
      WHERE status = 'PROCESSING' AND (next_query_time IS NULL OR next_query_time <= #{now})
      ORDER BY id LIMIT #{limit}
      """)
  @Results(id = "paymentRefundDueMap", value = {
      @Result(column = "id", property = "id"),
      @Result(column = "pay_order_id", property = "payOrderId"),
      @Result(column = "provider", property = "provider"),
      @Result(column = "merchant_order_no", property = "merchantOrderNo"),
      @Result(column = "refund_request_no", property = "refundRequestNo"),
      @Result(column = "amount_cent", property = "amountCent"),
      @Result(column = "status", property = "status"),
      @Result(column = "retry_count", property = "retryCount")
  })
  List<PaymentRefundRow> findDue(@Param("now") LocalDateTime now, @Param("limit") int limit);

  @Update("""
      UPDATE payment_refund
      SET status = 'SUCCESS', channel_transaction_no = #{channelTransactionNo},
          channel_refund_no = #{channelRefundNo}, raw_response = #{rawResponse}, next_query_time = NULL,
          last_error = NULL, update_time = #{now}
      WHERE id = #{id} AND status = 'PROCESSING'
      """)
  int markSuccess(@Param("id") long id, @Param("channelTransactionNo") String channelTransactionNo,
      @Param("channelRefundNo") String channelRefundNo, @Param("rawResponse") String rawResponse,
      @Param("now") LocalDateTime now);

  @Update("""
      UPDATE payment_refund
      SET retry_count = retry_count + 1, raw_response = #{rawResponse}, last_error = #{lastError},
          next_query_time = #{nextQueryTime}, update_time = #{now}
      WHERE id = #{id} AND status = 'PROCESSING'
      """)
  int recordPending(@Param("id") long id, @Param("rawResponse") String rawResponse,
      @Param("lastError") String lastError, @Param("nextQueryTime") LocalDateTime nextQueryTime,
      @Param("now") LocalDateTime now);
}
