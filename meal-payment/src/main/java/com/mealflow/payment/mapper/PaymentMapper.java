package com.mealflow.payment.mapper;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface PaymentMapper {
  @Select("SELECT COALESCE(MAX(id), 10000) FROM payment_order")
  long maxPaymentOrderId();

  @Insert("""
      INSERT INTO payment_order (id, order_id, amount_cent, status, create_time, update_time)
      VALUES (#{id}, #{orderId}, #{amountCent}, #{status}, #{now}, #{now})
      """)
  int insert(@Param("id") long id, @Param("orderId") long orderId, @Param("amountCent") int amountCent,
      @Param("status") String status, @Param("now") LocalDateTime now);

  @Update("""
      UPDATE payment_order
      SET status = #{status}, update_time = #{now}
      WHERE id = #{id} AND status IN (#{unpaid}, #{paying})
      """)
  int updatePayableStatus(@Param("id") long id, @Param("status") String status,
      @Param("unpaid") String unpaid, @Param("paying") String paying, @Param("now") LocalDateTime now);

  @Select("""
      SELECT id, order_id, amount_cent, status
      FROM payment_order
      WHERE id = #{id}
      """)
  @Results(id = "paymentOrderMap", value = {
      @Result(column = "id", property = "id"),
      @Result(column = "order_id", property = "orderId"),
      @Result(column = "amount_cent", property = "amountCent"),
      @Result(column = "status", property = "status")
  })
  PaymentOrderRow findById(long id);

  @Select("""
      SELECT id, order_id, amount_cent, status
      FROM payment_order
      ORDER BY id
      """)
  @ResultMap("paymentOrderMap")
  List<PaymentOrderRow> findAll();
}
