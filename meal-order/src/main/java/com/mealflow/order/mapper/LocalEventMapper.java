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
public interface LocalEventMapper {
  @Select("SELECT COALESCE(MAX(id), 10000) FROM order_local_event")
  long maxEventId();

  @Select("SELECT COUNT(*) FROM order_local_event WHERE status = #{status}")
  long countByStatus(String status);

  @Insert("""
      INSERT INTO order_local_event (
        id, event_key, event_type, event_version, aggregate_type, aggregate_id,
        payload_json, status, retry_count, last_error, create_time, update_time
      )
      VALUES (
        #{id}, #{eventKey}, #{eventType}, #{eventVersion}, #{aggregateType}, #{aggregateId},
        #{payloadJson}, #{status}, 0, NULL, #{now}, #{now}
      )
      """)
  int insert(@Param("id") long id, @Param("eventKey") String eventKey,
      @Param("eventType") String eventType, @Param("eventVersion") int eventVersion,
      @Param("aggregateType") String aggregateType, @Param("aggregateId") long aggregateId,
      @Param("payloadJson") String payloadJson, @Param("status") String status,
      @Param("now") LocalDateTime now);

  @Select("""
      SELECT id, event_key, event_type, event_version, aggregate_type, aggregate_id,
             payload_json, status, retry_count, last_error, create_time, update_time
      FROM order_local_event
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
      @Result(column = "create_time", property = "createTime"),
      @Result(column = "update_time", property = "updateTime")
  })
  List<LocalEventRow> findAll();

  @Select("""
      SELECT id, event_key, event_type, event_version, aggregate_type, aggregate_id,
             payload_json, status, retry_count, last_error, create_time, update_time
      FROM order_local_event
      WHERE status IN ('NEW', 'FAILED')
      ORDER BY id
      LIMIT #{limit}
      """)
  @ResultMap("localEventMap")
  List<LocalEventRow> findDispatchable(int limit);

  @Select("""
      SELECT id, event_key, event_type, event_version, aggregate_type, aggregate_id,
             payload_json, status, retry_count, last_error, create_time, update_time
      FROM order_local_event
      WHERE event_key = #{eventKey}
      """)
  @ResultMap("localEventMap")
  LocalEventRow findByEventKey(String eventKey);

  @Update("""
      UPDATE order_local_event
      SET status = 'SENDING', retry_count = retry_count + 1, update_time = #{now}
      WHERE id = #{id} AND status IN ('NEW', 'FAILED')
      """)
  int markSending(@Param("id") long id, @Param("now") LocalDateTime now);

  @Update("""
      UPDATE order_local_event
      SET status = 'FAILED', last_error = 'SENDING_TIMEOUT', update_time = #{now}
      WHERE status = 'SENDING' AND update_time < #{before}
      """)
  int markStaleSendingFailedBefore(@Param("before") LocalDateTime before, @Param("now") LocalDateTime now);

  @Update("""
      UPDATE order_local_event
      SET status = 'SENT', last_error = NULL, update_time = #{now}
      WHERE id = #{id} AND status = 'SENDING'
      """)
  int markSent(@Param("id") long id, @Param("now") LocalDateTime now);

  @Update("""
      UPDATE order_local_event
      SET status = 'FAILED', last_error = #{lastError}, update_time = #{now}
      WHERE id = #{id} AND status = 'SENDING'
      """)
  int markFailed(@Param("id") long id, @Param("lastError") String lastError, @Param("now") LocalDateTime now);
}
