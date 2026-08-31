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

  @Select("""
      SELECT id, ticket_no, request_id, user_id, merchant_id, status, score, ahead_count_snapshot,
             estimated_wait_seconds, expire_time, snapshot_json, order_id, ready_time, processing_time
      FROM queue_ticket
      WHERE user_id = #{userId} AND status IN ('WAITING', 'READY', 'PROCESSING') AND expire_time > #{now}
      ORDER BY create_time DESC LIMIT 1
      """)
  @ResultMap("ticketMap")
  QueueTicketRow findActiveTicketByUser(@Param("userId") long userId, @Param("now") LocalDateTime now);

  @Select("""
      SELECT id, ticket_no, request_id, user_id, merchant_id, status, score, ahead_count_snapshot,
             estimated_wait_seconds, expire_time, snapshot_json, order_id, ready_time, processing_time
      FROM queue_ticket
      WHERE user_id = #{userId}
      ORDER BY create_time DESC, id DESC
      LIMIT #{limit}
      """)
  @ResultMap("ticketMap")
  List<QueueTicketRow> findRecentTicketsByUser(@Param("userId") long userId, @Param("limit") int limit);

  @Select("""
      SELECT id, ticket_no, request_id, user_id, merchant_id, status, score, ahead_count_snapshot,
             estimated_wait_seconds, expire_time, snapshot_json, order_id, ready_time, processing_time
      FROM queue_ticket
      WHERE user_id = #{userId} AND request_id = #{requestId}
        AND status IN ('WAITING', 'READY', 'PROCESSING') AND expire_time > #{now}
      ORDER BY id DESC LIMIT 1
      """)
  @ResultMap("ticketMap")
  QueueTicketRow findTicketByRequest(@Param("userId") long userId, @Param("requestId") String requestId,
      @Param("now") LocalDateTime now);

  @Select("""
      SELECT id, ticket_no, request_id, user_id, merchant_id, status, score, ahead_count_snapshot,
             estimated_wait_seconds, expire_time, snapshot_json, order_id, ready_time, processing_time
      FROM queue_ticket
      WHERE status IN ('WAITING', 'READY') AND expire_time <= #{now}
      ORDER BY id
      """)
  @ResultMap("ticketMap")
  List<QueueTicketRow> findExpiredTickets(@Param("now") LocalDateTime now);

  @Select("""
      SELECT id, ticket_no, request_id, user_id, merchant_id, status, score, ahead_count_snapshot,
             estimated_wait_seconds, expire_time, snapshot_json, order_id, ready_time, processing_time
      FROM queue_ticket
      WHERE status IN ('READY', 'PROCESSING') AND order_id IS NULL AND expire_time > #{now}
      ORDER BY ready_time, id
      LIMIT #{limit}
      """)
  @ResultMap("ticketMap")
  List<QueueTicketRow> findRecoverableTickets(@Param("now") LocalDateTime now, @Param("limit") int limit);

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

  @Update("UPDATE queue_ticket SET status = #{targetStatus}, update_time = #{now} WHERE id = #{id} AND status = #{expectedStatus}")
  int expireTicket(@Param("id") long id, @Param("expectedStatus") String expectedStatus,
      @Param("targetStatus") String targetStatus, @Param("now") LocalDateTime now);

  @Insert("""
      INSERT INTO queue_timeout_notification (ticket_id, user_id, status, retry_count, create_time, update_time)
      VALUES (#{ticketId}, #{userId}, 'NEW', 0, #{now}, #{now})
      """)
  int enqueueTimeoutNotification(@Param("ticketId") long ticketId, @Param("userId") long userId,
      @Param("now") LocalDateTime now);

  @Select("""
      SELECT notification.ticket_id, notification.user_id, ticket.ticket_no
      FROM queue_timeout_notification notification
      JOIN queue_ticket ticket ON ticket.id = notification.ticket_id
      WHERE notification.status IN ('NEW', 'FAILED')
      ORDER BY notification.ticket_id
      LIMIT #{limit}
      """)
  @Results(id = "timeoutNotificationMap", value = {
      @Result(column = "ticket_id", property = "ticketId"),
      @Result(column = "user_id", property = "userId"),
      @Result(column = "ticket_no", property = "ticketNo")
  })
  List<QueueTimeoutNotificationRow> findDispatchableTimeoutNotifications(@Param("limit") int limit);

  @Update("""
      UPDATE queue_timeout_notification
      SET status = 'SENDING', retry_count = retry_count + 1, update_time = #{now}
      WHERE ticket_id = #{ticketId} AND status IN ('NEW', 'FAILED')
      """)
  int markTimeoutNotificationSending(@Param("ticketId") long ticketId, @Param("now") LocalDateTime now);

  @Update("""
      UPDATE queue_timeout_notification
      SET status = 'SENT', last_error = NULL, update_time = #{now}
      WHERE ticket_id = #{ticketId} AND status = 'SENDING'
      """)
  int markTimeoutNotificationSent(@Param("ticketId") long ticketId, @Param("now") LocalDateTime now);

  @Update("""
      UPDATE queue_timeout_notification
      SET status = 'FAILED', last_error = #{lastError}, update_time = #{now}
      WHERE ticket_id = #{ticketId} AND status = 'SENDING'
      """)
  int markTimeoutNotificationFailed(@Param("ticketId") long ticketId, @Param("lastError") String lastError,
      @Param("now") LocalDateTime now);

  @Update("""
      UPDATE queue_timeout_notification
      SET status = 'FAILED', last_error = 'SENDING_TIMEOUT', update_time = #{now}
      WHERE status = 'SENDING' AND update_time < #{before}
      """)
  int recoverStaleTimeoutNotifications(@Param("before") LocalDateTime before, @Param("now") LocalDateTime now);

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
      SELECT id, request_id, merchant_id, ticket_id, order_id, status, expire_time, release_reason,
             released_ticket_id, released_capacity_token_id
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
      @Result(column = "release_reason", property = "releaseReason"),
      @Result(column = "released_ticket_id", property = "releasedTicketId"),
      @Result(column = "released_capacity_token_id", property = "releasedCapacityTokenId")
  })
  CapacityTokenRow findToken(long id);

  @Select("""
      SELECT id, request_id, merchant_id, ticket_id, order_id, status, expire_time, release_reason,
             released_ticket_id, released_capacity_token_id
      FROM capacity_token
      WHERE request_id = #{requestId} AND merchant_id = #{merchantId} AND ticket_id IS NULL AND order_id IS NULL
        AND status = 'HELD' AND expire_time > #{now}
      ORDER BY id DESC LIMIT 1
      """)
  @ResultMap("tokenMap")
  CapacityTokenRow findDirectTokenByRequest(@Param("requestId") String requestId, @Param("merchantId") long merchantId,
      @Param("now") LocalDateTime now);

  @Select("""
      SELECT id, request_id, merchant_id, ticket_id, order_id, status, expire_time, release_reason,
             released_ticket_id, released_capacity_token_id
      FROM capacity_token
      WHERE status = #{status} AND ticket_id IS NULL AND order_id IS NULL AND expire_time <= #{now}
      ORDER BY id
      """)
  @ResultMap("tokenMap")
  List<CapacityTokenRow> findExpiredUnboundTokens(@Param("now") LocalDateTime now, @Param("status") String status);

  @Select("""
      SELECT id, request_id, merchant_id, ticket_id, order_id, status, expire_time, release_reason,
             released_ticket_id, released_capacity_token_id
      FROM capacity_token
      WHERE ticket_id = #{ticketId} AND status = #{status}
      ORDER BY id DESC
      LIMIT 1
      """)
  @ResultMap("tokenMap")
  CapacityTokenRow findHeldTokenByTicket(@Param("ticketId") long ticketId, @Param("status") String status);

  @Select("""
      SELECT id, request_id, merchant_id, ticket_id, order_id, status, expire_time, release_reason,
             released_ticket_id, released_capacity_token_id
      FROM capacity_token
      WHERE order_id = #{orderId}
      ORDER BY id DESC
      LIMIT 1
      """)
  @ResultMap("tokenMap")
  CapacityTokenRow findTokenByOrder(long orderId);

  @Select("""
      SELECT id, request_id, merchant_id, ticket_id, order_id, status, expire_time, release_reason,
             released_ticket_id, released_capacity_token_id
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
      SET status = #{toStatus}, release_reason = #{reason}, update_time = #{now}
      WHERE id = #{id} AND status = #{fromStatus}
      """)
  int updateTokenStatusFromStatus(@Param("id") long id, @Param("fromStatus") String fromStatus,
      @Param("toStatus") String toStatus, @Param("reason") String reason, @Param("now") LocalDateTime now);

  @Update("""
      UPDATE capacity_token
      SET released_ticket_id = #{ticketId}, released_capacity_token_id = #{releasedCapacityTokenId},
          update_time = #{now}
      WHERE id = #{id} AND status = 'RELEASED'
      """)
  int recordReleaseResult(@Param("id") long id, @Param("ticketId") Long ticketId,
      @Param("releasedCapacityTokenId") Long releasedCapacityTokenId, @Param("now") LocalDateTime now);

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

  @Select("""
      SELECT merchant_id, COUNT(*) AS held_count
      FROM capacity_token
      WHERE status = #{status}
      GROUP BY merchant_id
      """)
  @Results(id = "merchantHeldCountMap", value = {
      @Result(column = "merchant_id", property = "merchantId"),
      @Result(column = "held_count", property = "heldCount")
  })
  List<MerchantHeldCountRow> findHeldTokenCounts(String status);

  @Select("SELECT limit_value FROM merchant_queue_limit WHERE merchant_id = #{merchantId}")
  Integer findMerchantLimit(long merchantId);

  @Update("UPDATE merchant_queue_limit SET inflight_count = inflight_count + 1 WHERE merchant_id = #{merchantId} AND inflight_count < limit_value")
  int tryAcquireCapacity(long merchantId);

  @Insert("INSERT INTO merchant_queue_limit (merchant_id, limit_value, inflight_count, create_time, update_time) VALUES (#{merchantId}, 1, 0, #{now}, #{now}) ON DUPLICATE KEY UPDATE merchant_id = merchant_id")
  int ensureMerchantLimit(@Param("merchantId") long merchantId, @Param("now") LocalDateTime now);

  @Update("UPDATE merchant_queue_limit SET inflight_count = CASE WHEN inflight_count > 0 THEN inflight_count - 1 ELSE 0 END WHERE merchant_id = #{merchantId}")
  int releaseCapacity(long merchantId);

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
