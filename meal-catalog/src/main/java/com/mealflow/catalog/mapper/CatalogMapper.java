package com.mealflow.catalog.mapper;

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
public interface CatalogMapper {
  @Select("SELECT id, merchant_id, name, price_cent, stock FROM sku WHERE merchant_id = #{merchantId} ORDER BY id")
  @Results(id = "skuMap", value = {
      @Result(column = "id", property = "id"),
      @Result(column = "merchant_id", property = "merchantId"),
      @Result(column = "name", property = "name"),
      @Result(column = "price_cent", property = "priceCent"),
      @Result(column = "stock", property = "stock")
  })
  List<SkuRow> findSkusByMerchant(long merchantId);

  @Select("SELECT id, merchant_id, name, price_cent, stock FROM sku WHERE id = #{id}")
  @ResultMap("skuMap")
  SkuRow findSku(long id);

  @Update("""
      UPDATE sku
      SET stock = stock - #{quantity}
      WHERE id = #{skuId} AND merchant_id = #{merchantId} AND stock >= #{quantity}
      """)
  int decrementStock(@Param("skuId") long skuId, @Param("merchantId") long merchantId,
      @Param("quantity") int quantity);

  @Update("UPDATE sku SET stock = stock + #{quantity} WHERE id = #{skuId}")
  int restoreStock(@Param("skuId") long skuId, @Param("quantity") int quantity);

  @Insert("""
      INSERT INTO stock_reservation(
        id, request_id, user_id, merchant_id, sku_id, ticket_id, order_id, quantity, status,
        expire_time, create_time, update_time
      ) VALUES (
        #{id}, #{requestId}, #{userId}, #{merchantId}, #{skuId}, #{ticketId}, #{orderId}, #{quantity},
        #{status}, #{expireTime}, #{now}, #{now}
      )
      """)
  int insertReservation(@Param("id") long id, @Param("requestId") String requestId, @Param("userId") long userId,
      @Param("merchantId") long merchantId, @Param("skuId") long skuId, @Param("ticketId") Long ticketId,
      @Param("orderId") Long orderId, @Param("quantity") int quantity, @Param("status") int status,
      @Param("expireTime") LocalDateTime expireTime, @Param("now") LocalDateTime now);

  @Select("SELECT id FROM stock_reservation WHERE request_id = #{requestId} AND sku_id = #{skuId}")
  Long findReservationIdByRequestSku(@Param("requestId") String requestId, @Param("skuId") long skuId);

  @Update("""
      UPDATE stock_reservation
      SET status = #{status}, order_id = #{orderId}, update_time = #{now}
      WHERE id = #{id} AND status = #{expectedStatus}
      """)
  int confirmReservation(@Param("id") long id, @Param("status") int status, @Param("orderId") Long orderId,
      @Param("expectedStatus") int expectedStatus, @Param("now") LocalDateTime now);

  @Update("""
      UPDATE stock_reservation
      SET status = #{status}, update_time = #{now}
      WHERE id = #{id} AND status = #{expectedStatus}
      """)
  int releaseReservation(@Param("id") long id, @Param("status") int status,
      @Param("expectedStatus") int expectedStatus, @Param("now") LocalDateTime now);

  @Select("SELECT id, sku_id, quantity, status, ticket_id, order_id FROM stock_reservation WHERE id = #{id}")
  @Results(id = "reservationMap", value = {
      @Result(column = "id", property = "id"),
      @Result(column = "sku_id", property = "skuId"),
      @Result(column = "quantity", property = "quantity"),
      @Result(column = "status", property = "status"),
      @Result(column = "ticket_id", property = "ticketId"),
      @Result(column = "order_id", property = "orderId")
  })
  StockReservationRow findReservation(long id);

  @Select("SELECT id, sku_id, quantity, status, ticket_id, order_id FROM stock_reservation ORDER BY id")
  @ResultMap("reservationMap")
  List<StockReservationRow> findReservations();
}
