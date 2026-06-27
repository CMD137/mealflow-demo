package com.mealflow.queue.mapper;

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
public interface QueueMapper {
  @Select("""
      SELECT id, ticket_no, merchant_id, score
      FROM queue_ticket
      WHERE status = #{status} AND expire_time > #{now}
      ORDER BY score, ticket_no
      """)
  @Results(id = "waitingTicketMap", value = {
      @Result(column = "id", property = "id"),
      @Result(column = "ticket_no", property = "ticketNo"),
      @Result(column = "merchant_id", property = "merchantId"),
      @Result(column = "score", property = "score")
  })
  List<WaitingTicketRow> findWaitingTickets(@Param("status") String status, @Param("now") LocalDateTime now);

  @Insert("""
      INSERT INTO queue_ticket (
        id, ticket_no, request_id, user_id, merchant_id, status, score, ahead_count_snapshot,
        estimated_wait_seconds, expire_time, snapshot_json, order_id, ready_time, processing_time,
        create_time, update_time
      )
      VALUES (
        #{id}, #{ticketNo}, #{requestId}, #{userId}, #{merchantId}, #{status}, #{score},
        #{aheadCountSnapshot}, #{estimatedWaitSeconds}, #{expireTime}, #{snapshotJson},
        #{orderId}, #{readyTime}, #{processingTime}, #{now}, #{now}
      )
      """)
  int insertTicket(@Param("id") long id, @Param("ticketNo") String ticketNo, @Param("requestId") String requestId,
      @Param("userId") long userId, @Param("merchantId") long merchantId, @Param("status") String status,
      @Param("score") long score, @Param("aheadCountSnapshot") int aheadCountSnapshot,
      @Param("estimatedWaitSeconds") int estimatedWaitSeconds, @Param("expireTime") LocalDateTime expireTime,
      @Param("snapshotJson") String snapshotJson, @Param("orderId") Long orderId,
      @Param("readyTime") LocalDateTime readyTime, @Param("processingTime") LocalDateTime processingTime,
      @Param("now") LocalDateTime now);

  @Select("""
      SELECT id, ticket_no, request_id, user_id, merchant_id, status, score, ahead_count_snapshot,
             estimated_wait_seconds, expire_time, snapshot_json, order_id, ready_time, processing_time
      FROM queue_ticket
      WHERE id = #{id}
      """)
  @Results(id = "ticketMap", value = {
      @Result(column = "id", property = "id"),
      @Result(column = "ticket_no", property = "ticketNo"),
      @Result(column = "request_id", property = "requestId"),
      @Result(column = "user_id", property = "userId"),
      @Result(column = "merchant_id", property = "merchantId"),
      @Result(column = "status", property = "status"),
      @Result(column = "score", property = "score"),
      @Result(column = "ahead_count_snapshot", property = "aheadCountSnapshot"),
      @Result(column = "estimated_wait_seconds", property = "estimatedWaitSeconds"),
      @Result(column = "expire_time", property = "expireTime"),
      @Result(column = "snapshot_json", property = "snapshotJson"),
      @Result(column = "order_id", property = "orderId"),
      @Result(column = "ready_time", property = "readyTime"),
      @Result(column = "processing_time", property = "processingTime")
  })
  QueueTicketRow findTicket(long id);

  @Select("SELECT id FROM queue_ticket ORDER BY id")
  List<Long> findTicketIds();

  @Update("""
      UPDATE queue_ticket
      SET status = #{status}, order_id = #{orderId}, ready_time = #{readyTime},
          processing_time = #{processingTime}, update_time = #{now}
      WHERE id = #{id}
      """)
  int updateTicketStatus(@Param("id") long id, @Param("status") String status, @Param("orderId") Long orderId,
      @Param("readyTime") LocalDateTime readyTime, @Param("processingTime") LocalDateTime processingTime,
      @Param("now") LocalDateTime now);

  @Insert("""
      INSERT INTO capacity_token (
        id, request_id, merchant_id, ticket_id, order_id, status, expire_time, release_reason,
        create_time, update_time
      )
      VALUES (
        #{id}, #{requestId}, #{merchantId}, #{ticketId}, #{orderId}, #{status}, #{expireTime},
        #{releaseReason}, #{now}, #{now}
      )
      """)
  int insertToken(@Param("id") long id, @Param("requestId") String requestId, @Param("merchantId") long merchantId,
      @Param("ticketId") Long ticketId, @Param("orderId") Long orderId, @Param("status") String status,
      @Param("expireTime") LocalDateTime expireTime, @Param("releaseReason") String releaseReason,
      @Param("now") LocalDateTime now);

  @Select("""
      SELECT id, request_id, merchant_id, ticket_id, order_id, status, expire_time, release_reason
      FROM capacity_token
      WHERE id = #{id}
      """)
  @Results(id = "tokenMap", value = {
      @Result(column = "id", property = "id"),
      @Result(column = "request_id", property = "requestId"),
      @Result(column = "merchant_id", property = "merchantId"),
      @Result(column = "ticket_id", property = "ticketId"),
      @Result(column = "order_id", property = "orderId"),
      @Result(column = "status", property = "status"),
      @Result(column = "expire_time", property = "expireTime"),
      @Result(column = "release_reason", property = "releaseReason")
  })
  CapacityTokenRow findToken(long id);

  @Select("""
      SELECT id, request_id, merchant_id, ticket_id, order_id, status, expire_time, release_reason
      FROM capacity_token
      WHERE ticket_id = #{ticketId} AND status = #{status}
      ORDER BY id DESC
      LIMIT 1
      """)
  @ResultMap("tokenMap")
  CapacityTokenRow findHeldTokenByTicket(@Param("ticketId") long ticketId, @Param("status") String status);

  @Select("""
      SELECT id, request_id, merchant_id, ticket_id, order_id, status, expire_time, release_reason
      FROM capacity_token
      WHERE order_id = #{orderId}
      ORDER BY id DESC
      LIMIT 1
      """)
  @ResultMap("tokenMap")
  CapacityTokenRow findTokenByOrder(long orderId);

  @Select("""
      SELECT id, request_id, merchant_id, ticket_id, order_id, status, expire_time, release_reason
      FROM capacity_token
      ORDER BY id
      """)
  @ResultMap("tokenMap")
  List<CapacityTokenRow> findTokens();

  @Update("""
      UPDATE capacity_token
      SET status = #{status}, release_reason = #{reason}, update_time = #{now}
      WHERE id = #{id}
      """)
  int updateTokenStatus(@Param("id") long id, @Param("status") String status, @Param("reason") String reason,
      @Param("now") LocalDateTime now);

  @Update("""
      UPDATE capacity_token
      SET order_id = #{orderId}, update_time = #{now}
      WHERE id = #{id}
      """)
  int bindTokenOrder(@Param("id") long id, @Param("orderId") long orderId, @Param("now") LocalDateTime now);

  @Update("""
      UPDATE capacity_token
      SET order_id = #{orderId}, update_time = #{now}
      WHERE ticket_id = #{ticketId}
      """)
  int bindTicketTokensOrder(@Param("ticketId") long ticketId, @Param("orderId") long orderId,
      @Param("now") LocalDateTime now);

  @Select("SELECT COUNT(*) FROM capacity_token WHERE merchant_id = #{merchantId} AND status = #{status}")
  int countHeldTokens(@Param("merchantId") long merchantId, @Param("status") String status);

  @Select("SELECT limit_value FROM merchant_queue_limit WHERE merchant_id = #{merchantId}")
  Integer findMerchantLimit(long merchantId);

  @Insert("""
      INSERT INTO merchant_queue_limit (merchant_id, limit_value, create_time, update_time)
      VALUES (#{merchantId}, #{limit}, #{now}, #{now})
      """)
  int insertMerchantLimit(@Param("merchantId") long merchantId, @Param("limit") int limit,
      @Param("now") LocalDateTime now);

  @Update("""
      UPDATE merchant_queue_limit
      SET limit_value = #{limit}, update_time = #{now}
      WHERE merchant_id = #{merchantId}
      """)
  int updateMerchantLimit(@Param("merchantId") long merchantId, @Param("limit") int limit,
      @Param("now") LocalDateTime now);
}
