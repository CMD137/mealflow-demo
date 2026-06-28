package com.mealflow.fulfillment.mapper;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface FulfillmentMapper {
  @Select("SELECT COALESCE(MAX(id), 10000) FROM fulfillment_operation_log")
  long maxOperationId();

  @Insert("""
      INSERT INTO fulfillment_operation_log (
        id, request_id, order_id, action, status, message, create_time
      )
      VALUES (
        #{id}, #{requestId}, #{orderId}, #{action}, #{status}, #{message}, #{createTime}
      )
      """)
  int insertOperation(@Param("id") long id, @Param("requestId") String requestId,
      @Param("orderId") long orderId, @Param("action") String action, @Param("status") String status,
      @Param("message") String message, @Param("createTime") LocalDateTime createTime);

  @Select("""
      SELECT id, request_id, order_id, action, status, message, create_time
      FROM fulfillment_operation_log
      ORDER BY id
      """)
  @Results(id = "operationMap", value = {
      @Result(column = "id", property = "id"),
      @Result(column = "request_id", property = "requestId"),
      @Result(column = "order_id", property = "orderId"),
      @Result(column = "action", property = "action"),
      @Result(column = "status", property = "status"),
      @Result(column = "message", property = "message"),
      @Result(column = "create_time", property = "createTime")
  })
  List<FulfillmentOperationRow> findOperations();
}
