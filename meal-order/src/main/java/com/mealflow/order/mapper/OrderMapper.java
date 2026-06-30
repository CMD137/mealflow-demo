package com.mealflow.order.mapper;

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
public interface OrderMapper {
  String ORDER_COLUMNS = """
      id, user_id, merchant_id, status, queue_ticket_id, capacity_token_id, pay_order_id,
      reservation_ids_json, voucher_lock_id, items_json, amount_cent
      """;

  @Select("SELECT COALESCE(MAX(id), 10000) FROM customer_order")
  long maxOrderId();

  @Insert("""
      INSERT INTO customer_order (
        id, user_id, merchant_id, status, queue_ticket_id, capacity_token_id, pay_order_id,
        reservation_ids_json, voucher_lock_id, items_json, amount_cent, create_time, update_time
      )
      VALUES (
        #{id}, #{userId}, #{merchantId}, #{status}, #{queueTicketId}, #{capacityTokenId}, #{payOrderId},
        #{reservationIdsJson}, #{voucherLockId}, #{itemsJson}, #{amountCent}, #{now}, #{now}
      )
      """)
  int insert(@Param("id") long id, @Param("userId") long userId, @Param("merchantId") long merchantId,
      @Param("status") String status, @Param("queueTicketId") Long queueTicketId,
      @Param("capacityTokenId") long capacityTokenId, @Param("payOrderId") long payOrderId,
      @Param("reservationIdsJson") String reservationIdsJson, @Param("voucherLockId") Long voucherLockId,
      @Param("itemsJson") String itemsJson, @Param("amountCent") int amountCent,
      @Param("now") LocalDateTime now);

  @Update("""
      UPDATE customer_order
      SET status = #{status}, update_time = #{now}
      WHERE id = #{id}
      """)
  int updateStatus(@Param("id") long id, @Param("status") String status, @Param("now") LocalDateTime now);

  @Select("SELECT " + ORDER_COLUMNS + " FROM customer_order WHERE id = #{id}")
  @Results(id = "orderMap", value = {
      @Result(column = "id", property = "id"),
      @Result(column = "user_id", property = "userId"),
      @Result(column = "merchant_id", property = "merchantId"),
      @Result(column = "status", property = "status"),
      @Result(column = "queue_ticket_id", property = "queueTicketId"),
      @Result(column = "capacity_token_id", property = "capacityTokenId"),
      @Result(column = "pay_order_id", property = "payOrderId"),
      @Result(column = "reservation_ids_json", property = "reservationIdsJson"),
      @Result(column = "voucher_lock_id", property = "voucherLockId"),
      @Result(column = "items_json", property = "itemsJson"),
      @Result(column = "amount_cent", property = "amountCent")
  })
  OrderRow findById(long id);

  @Select("SELECT " + ORDER_COLUMNS + " FROM customer_order WHERE queue_ticket_id = #{ticketId} ORDER BY id DESC LIMIT 1")
  @ResultMap("orderMap")
  OrderRow findByTicketId(long ticketId);

  @Select("SELECT " + ORDER_COLUMNS + " FROM customer_order ORDER BY id")
  @ResultMap("orderMap")
  List<OrderRow> findAll();

  @Select("""
      <script>
      SELECT
      """ + ORDER_COLUMNS + """
      FROM customer_order
      WHERE 1 = 1
      <if test="merchantId != null">
        AND merchant_id = #{merchantId}
      </if>
      <if test="userId != null">
        AND user_id = #{userId}
      </if>
      <if test="status != null and status != ''">
        AND status = #{status}
      </if>
      <if test="beginTime != null">
        AND create_time &gt;= #{beginTime}
      </if>
      <if test="endTime != null">
        AND create_time &lt;= #{endTime}
      </if>
      ORDER BY id DESC
      </script>
      """)
  @ResultMap("orderMap")
  List<OrderRow> findAdminOrders(@Param("merchantId") Long merchantId, @Param("userId") Long userId,
      @Param("status") String status, @Param("beginTime") LocalDateTime beginTime,
      @Param("endTime") LocalDateTime endTime);

  @Select("""
      <script>
      SELECT status, COUNT(*) AS count
      FROM customer_order
      WHERE 1 = 1
      <if test="merchantId != null">
        AND merchant_id = #{merchantId}
      </if>
      <if test="beginTime != null">
        AND create_time &gt;= #{beginTime}
      </if>
      <if test="endTime != null">
        AND create_time &lt;= #{endTime}
      </if>
      GROUP BY status
      </script>
      """)
  @Results(id = "statusCountMap", value = {
      @Result(column = "status", property = "status"),
      @Result(column = "count", property = "count")
  })
  List<StatusCountRow> countByStatus(@Param("merchantId") Long merchantId,
      @Param("beginTime") LocalDateTime beginTime, @Param("endTime") LocalDateTime endTime);

  @Select("""
      <script>
      SELECT COALESCE(SUM(amount_cent), 0)
      FROM customer_order
      WHERE status = 'COMPLETED'
      <if test="merchantId != null">
        AND merchant_id = #{merchantId}
      </if>
      <if test="beginTime != null">
        AND create_time &gt;= #{beginTime}
      </if>
      <if test="endTime != null">
        AND create_time &lt;= #{endTime}
      </if>
      </script>
      """)
  int sumCompletedAmount(@Param("merchantId") Long merchantId, @Param("beginTime") LocalDateTime beginTime,
      @Param("endTime") LocalDateTime endTime);
}
