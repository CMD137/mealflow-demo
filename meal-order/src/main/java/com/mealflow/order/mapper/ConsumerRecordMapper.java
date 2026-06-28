package com.mealflow.order.mapper;

import com.mealflow.infra.consumer.PersistentConsumerRecordRepository;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ConsumerRecordMapper extends PersistentConsumerRecordRepository {
  @Select("SELECT COALESCE(MAX(id), 10000) FROM order_consumer_record")
  long maxRecordId();

  @Override
  @Select("""
      SELECT status
      FROM order_consumer_record
      WHERE event_key = #{eventKey} AND consumer_group = #{consumerGroup}
      """)
  String findStatus(@Param("eventKey") String eventKey, @Param("consumerGroup") String consumerGroup);

  @Override
  @Insert("""
      INSERT INTO order_consumer_record (
        id, event_key, consumer_group, status, last_error, create_time, update_time
      )
      VALUES (
        #{id}, #{eventKey}, #{consumerGroup}, 'PROCESSING', NULL, #{now}, #{now}
      )
      """)
  int insertProcessing(@Param("id") long id, @Param("eventKey") String eventKey,
      @Param("consumerGroup") String consumerGroup, @Param("now") LocalDateTime now);

  @Override
  @Update("""
      UPDATE order_consumer_record
      SET status = 'PROCESSING', last_error = NULL, update_time = #{now}
      WHERE event_key = #{eventKey} AND consumer_group = #{consumerGroup}
        AND status IN ('FAILED', 'TIMEOUT')
      """)
  int markProcessing(@Param("eventKey") String eventKey, @Param("consumerGroup") String consumerGroup,
      @Param("now") LocalDateTime now);

  @Override
  @Update("""
      UPDATE order_consumer_record
      SET status = 'SUCCESS', last_error = NULL, update_time = #{now}
      WHERE event_key = #{eventKey} AND consumer_group = #{consumerGroup}
        AND status = 'PROCESSING'
      """)
  int markSuccess(@Param("eventKey") String eventKey, @Param("consumerGroup") String consumerGroup,
      @Param("now") LocalDateTime now);

  @Override
  @Update("""
      UPDATE order_consumer_record
      SET status = 'FAILED', last_error = #{lastError}, update_time = #{now}
      WHERE event_key = #{eventKey} AND consumer_group = #{consumerGroup}
        AND status = 'PROCESSING'
      """)
  int markFailed(@Param("eventKey") String eventKey, @Param("consumerGroup") String consumerGroup,
      @Param("lastError") String lastError, @Param("now") LocalDateTime now);
}
