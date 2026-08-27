package com.mealflow.order.mapper;

import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface IdempotencyRecordMapper {
  @Insert("INSERT IGNORE INTO idempotency_record (subject, idempotency_key, request_hash, status, lease_expire_time, create_time, update_time) VALUES (#{subject}, #{key}, #{requestHash}, 'PROCESSING', #{leaseExpireTime}, #{now}, #{now})")
  int insertProcessing(@Param("subject") String subject, @Param("key") String key,
      @Param("requestHash") String requestHash, @Param("leaseExpireTime") LocalDateTime leaseExpireTime,
      @Param("now") LocalDateTime now);

  @Select("SELECT request_hash, status, response_json, lease_expire_time FROM idempotency_record WHERE subject = #{subject} AND idempotency_key = #{key}")
  @Results(id = "idempotencyRecordMap", value = {
      @Result(column = "request_hash", property = "requestHash"),
      @Result(column = "status", property = "status"),
      @Result(column = "response_json", property = "responseJson"),
      @Result(column = "lease_expire_time", property = "leaseExpireTime")
  })
  IdempotencyRecordRow find(@Param("subject") String subject, @Param("key") String key);

  @Update("UPDATE idempotency_record SET status = 'SUCCESS', response_json = #{responseJson}, lease_expire_time = NULL, update_time = #{now} WHERE subject = #{subject} AND idempotency_key = #{key} AND status = 'PROCESSING'")
  int complete(@Param("subject") String subject, @Param("key") String key, @Param("responseJson") String responseJson,
      @Param("now") LocalDateTime now);

  @Update("UPDATE idempotency_record SET status = 'PROCESSING', lease_expire_time = #{leaseExpireTime}, update_time = #{now} WHERE subject = #{subject} AND idempotency_key = #{key} AND status IN ('FAILED', 'PROCESSING') AND lease_expire_time < #{now}")
  int takeOverExpired(@Param("subject") String subject, @Param("key") String key,
      @Param("leaseExpireTime") LocalDateTime leaseExpireTime, @Param("now") LocalDateTime now);

  @Update("UPDATE idempotency_record SET status = 'FAILED', lease_expire_time = #{now}, update_time = #{now} WHERE subject = #{subject} AND idempotency_key = #{key} AND status = 'PROCESSING'")
  int markFailed(@Param("subject") String subject, @Param("key") String key, @Param("now") LocalDateTime now);
}
