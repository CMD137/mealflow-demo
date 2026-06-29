package com.mealflow.notify.mapper;

import com.mealflow.infra.consumer.PersistentConsumerRecordRepository;
import com.mealflow.infra.consumer.PersistentConsumerRecordState;
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
public interface ConsumerRecordMapper extends PersistentConsumerRecordRepository {
  @Select("SELECT COALESCE(MAX(id), 10000) FROM consumer_record")
  long maxRecordId();

  @Select("""
      SELECT COUNT(*)
      FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_NAME = 'consumer_record' AND COLUMN_NAME = #{columnName}
      """)
  int countColumn(@Param("columnName") String columnName);

  @Update("ALTER TABLE consumer_record ADD COLUMN event_type VARCHAR(128) NULL")
  int addEventTypeColumn();

  @Update("ALTER TABLE consumer_record ADD COLUMN payload_json TEXT NULL")
  int addPayloadJsonColumn();

  @Override
  @Select("""
      SELECT status, event_type, payload_json, update_time
      FROM consumer_record
      WHERE event_key = #{eventKey} AND consumer_group = #{consumerGroup}
      """)
  @Results(id = "consumerRecordStateMap", value = {
      @Result(column = "status", property = "status"),
      @Result(column = "event_type", property = "eventType"),
      @Result(column = "payload_json", property = "payloadJson"),
      @Result(column = "update_time", property = "updateTime")
  })
  PersistentConsumerRecordState findRecord(@Param("eventKey") String eventKey,
      @Param("consumerGroup") String consumerGroup);

  @Override
  @Select("""
      SELECT status
      FROM consumer_record
      WHERE event_key = #{eventKey} AND consumer_group = #{consumerGroup}
      """)
  String findStatus(@Param("eventKey") String eventKey, @Param("consumerGroup") String consumerGroup);

  @Override
  @Insert("""
      INSERT INTO consumer_record (
        id, event_key, consumer_group, event_type, payload_json, status, last_error, create_time, update_time
      )
      VALUES (
        #{id}, #{eventKey}, #{consumerGroup}, #{eventType}, #{payloadJson}, 'PROCESSING', NULL, #{now}, #{now}
      )
      """)
  int insertProcessing(@Param("id") long id, @Param("eventKey") String eventKey,
      @Param("consumerGroup") String consumerGroup, @Param("eventType") String eventType,
      @Param("payloadJson") String payloadJson, @Param("now") LocalDateTime now);

  @Override
  @Update("""
      UPDATE consumer_record
      SET status = 'PROCESSING',
        event_type = COALESCE(#{eventType}, event_type),
        payload_json = COALESCE(#{payloadJson}, payload_json),
        last_error = NULL,
        update_time = #{now}
      WHERE event_key = #{eventKey} AND consumer_group = #{consumerGroup}
        AND status IN ('FAILED', 'TIMEOUT')
      """)
  int markProcessing(@Param("eventKey") String eventKey, @Param("consumerGroup") String consumerGroup,
      @Param("eventType") String eventType, @Param("payloadJson") String payloadJson,
      @Param("now") LocalDateTime now);

  @Override
  @Update("""
      UPDATE consumer_record
      SET status = 'TIMEOUT', last_error = 'PROCESSING_TIMEOUT', update_time = #{now}
      WHERE event_key = #{eventKey} AND consumer_group = #{consumerGroup}
        AND status = 'PROCESSING' AND update_time < #{before}
      """)
  int markTimeoutBefore(@Param("eventKey") String eventKey, @Param("consumerGroup") String consumerGroup,
      @Param("before") LocalDateTime before, @Param("now") LocalDateTime now);

  @Override
  @Update("""
      UPDATE consumer_record
      SET status = 'TIMEOUT', last_error = 'PROCESSING_TIMEOUT', update_time = #{now}
      WHERE status = 'PROCESSING' AND update_time < #{before}
      """)
  int markProcessingTimeoutsBefore(@Param("before") LocalDateTime before, @Param("now") LocalDateTime now);

  @Override
  @Update("""
      UPDATE consumer_record
      SET status = 'SUCCESS', last_error = NULL, update_time = #{now}
      WHERE event_key = #{eventKey} AND consumer_group = #{consumerGroup}
        AND status = 'PROCESSING'
      """)
  int markSuccess(@Param("eventKey") String eventKey, @Param("consumerGroup") String consumerGroup,
      @Param("now") LocalDateTime now);

  @Override
  @Update("""
      UPDATE consumer_record
      SET status = 'FAILED', last_error = #{lastError}, update_time = #{now}
      WHERE event_key = #{eventKey} AND consumer_group = #{consumerGroup}
        AND status = 'PROCESSING'
      """)
  int markFailed(@Param("eventKey") String eventKey, @Param("consumerGroup") String consumerGroup,
      @Param("lastError") String lastError, @Param("now") LocalDateTime now);

  @Select("""
      SELECT id, event_key, consumer_group, event_type, payload_json, status, last_error, create_time, update_time
      FROM consumer_record
      ORDER BY id
      """)
  @Results(id = "consumerRecordMap", value = {
      @Result(column = "id", property = "id"),
      @Result(column = "event_key", property = "eventKey"),
      @Result(column = "consumer_group", property = "consumerGroup"),
      @Result(column = "event_type", property = "eventType"),
      @Result(column = "payload_json", property = "payloadJson"),
      @Result(column = "status", property = "status"),
      @Result(column = "last_error", property = "lastError"),
      @Result(column = "create_time", property = "createTime"),
      @Result(column = "update_time", property = "updateTime")
  })
  List<ConsumerRecordRow> findAll();

  @Select("""
      SELECT id, event_key, consumer_group, event_type, payload_json, status, last_error, create_time, update_time
      FROM consumer_record
      WHERE event_key = #{eventKey} AND consumer_group = #{consumerGroup}
      """)
  @ResultMap("consumerRecordMap")
  ConsumerRecordRow findByEvent(@Param("eventKey") String eventKey, @Param("consumerGroup") String consumerGroup);
}
