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
public interface LocalEventMapper {
  @Select("SELECT COALESCE(MAX(id), 10000) FROM payment_local_event")
  long maxEventId();

  @Select("SELECT COUNT(*) FROM payment_local_event WHERE status = #{status}")
  long countByStatus(String status);

  @Insert("""
      INSERT INTO payment_local_event (
        id, event_key, event_type, event_version, aggregate_type, aggregate_id,
        payload_json, status, retry_count, last_error, next_retry_time, lease_until, create_time, update_time
      )
      VALUES (
        #{id}, #{eventKey}, #{eventType}, #{eventVersion}, #{aggregateType}, #{aggregateId},
        #{payloadJson}, #{status}, 0, NULL, #{now}, NULL, #{now}, #{now}
      )
      """)
  int insert(@Param("id") long id, @Param("eventKey") String eventKey,
      @Param("eventType") String eventType, @Param("eventVersion") int eventVersion,
      @Param("aggregateType") String aggregateType, @Param("aggregateId") long aggregateId,
      @Param("payloadJson") String payloadJson, @Param("status") String status,
      @Param("now") LocalDateTime now);

  @Select("""
      SELECT id, event_key, event_type, event_version, aggregate_type, aggregate_id,
             payload_json, status, retry_count, last_error, next_retry_time, lease_until, create_time, update_time
      FROM payment_local_event
      ORDER BY id
      """)
  @Results(id = "localEventMap", value = {
      @Result(column = "id", property = "id"),
      @Result(column = "event_key", property = "eventKey"),
      @Result(column = "event_type", property = "eventType"),
      @Result(column = "event_version", property = "eventVersion"),
      @Result(column = "aggregate_type", property = "aggregateType"),
      @Result(column = "aggregate_id", property = "aggregateId"),
      @Result(column = "payload_json", property = "payloadJson"),
      @Result(column = "status", property = "status"),
      @Result(column = "retry_count", property = "retryCount"),
      @Result(column = "last_error", property = "lastError"),
      @Result(column = "next_retry_time", property = "nextRetryTime"),
      @Result(column = "lease_until", property = "leaseUntil"),
      @Result(column = "create_time", property = "createTime"),
      @Result(column = "update_time", property = "updateTime")
  })
  List<LocalEventRow> findAll();

  @Select("""
      SELECT id, event_key, event_type, event_version, aggregate_type, aggregate_id,
             payload_json, status, retry_count, last_error, next_retry_time, lease_until, create_time, update_time
      FROM payment_local_event
      WHERE status IN ('NEW', 'FAILED') AND (next_retry_time IS NULL OR next_retry_time <= #{now})
      ORDER BY id
      LIMIT #{limit}
      """)
  @ResultMap("localEventMap")
  List<LocalEventRow> findDispatchable(@Param("now") LocalDateTime now, @Param("limit") int limit);

  @Select("""
      SELECT id, event_key, event_type, event_version, aggregate_type, aggregate_id,
             payload_json, status, retry_count, last_error, next_retry_time, lease_until, create_time, update_time
      FROM payment_local_event
      WHERE event_key = #{eventKey}
      """)
  @ResultMap("localEventMap")
  LocalEventRow findByEventKey(String eventKey);

  @Update("""
      UPDATE payment_local_event
      SET status = 'SENDING', retry_count = retry_count + 1, lease_until = #{leaseUntil}, update_time = #{now}
      WHERE id = #{id} AND status IN ('NEW', 'FAILED') AND (next_retry_time IS NULL OR next_retry_time <= #{now})
      """)
  int markSending(@Param("id") long id, @Param("now") LocalDateTime now, @Param("leaseUntil") LocalDateTime leaseUntil);

  @Update("""
      UPDATE payment_local_event
      SET status = CASE WHEN retry_count >= #{maxAttempts} THEN 'DEAD' ELSE 'FAILED' END,
          last_error = 'SENDING_TIMEOUT', next_retry_time = #{nextRetryTime}, lease_until = NULL, update_time = #{now}
      WHERE status = 'SENDING' AND lease_until < #{now}
      """)
  int markExpiredLeases(@Param("now") LocalDateTime now, @Param("nextRetryTime") LocalDateTime nextRetryTime,
      @Param("maxAttempts") int maxAttempts);

  @Update("""
      UPDATE payment_local_event
      SET status = 'SENT', last_error = NULL, lease_until = NULL, update_time = #{now}
      WHERE id = #{id} AND status = 'SENDING'
      """)
  int markSent(@Param("id") long id, @Param("now") LocalDateTime now);

  @Update("""
      UPDATE payment_local_event
      SET status = CASE WHEN retry_count >= #{maxAttempts} THEN 'DEAD' ELSE 'FAILED' END,
          last_error = #{lastError}, next_retry_time = #{nextRetryTime}, lease_until = NULL, update_time = #{now}
      WHERE id = #{id} AND status = 'SENDING'
      """)
  int markFailed(@Param("id") long id, @Param("lastError") String lastError, @Param("now") LocalDateTime now,
      @Param("nextRetryTime") LocalDateTime nextRetryTime, @Param("maxAttempts") int maxAttempts);
}
