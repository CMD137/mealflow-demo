package com.mealflow.payment.mapper;

import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface PaymentIdempotencyMapper {
  @Insert("INSERT IGNORE INTO payment_idempotency_record (subject, idempotency_key, request_hash, status, lease_expire_time, create_time, update_time) VALUES (#{subject}, #{key}, #{hash}, 'PROCESSING', #{lease}, #{now}, #{now})")
  int insertProcessing(@Param("subject") String subject, @Param("key") String key, @Param("hash") String hash,
      @Param("lease") LocalDateTime lease, @Param("now") LocalDateTime now);

  @Select("SELECT request_hash, status, response_json, lease_expire_time FROM payment_idempotency_record WHERE subject = #{subject} AND idempotency_key = #{key}")
  @Results(id = "paymentIdempotencyMap", value = {
      @Result(column = "request_hash", property = "requestHash"), @Result(column = "status", property = "status"),
      @Result(column = "response_json", property = "responseJson"), @Result(column = "lease_expire_time", property = "leaseExpireTime")})
  PaymentIdempotencyRow find(@Param("subject") String subject, @Param("key") String key);

  @Update("UPDATE payment_idempotency_record SET status = 'SUCCESS', response_json = #{response}, lease_expire_time = NULL, update_time = #{now} WHERE subject = #{subject} AND idempotency_key = #{key} AND status = 'PROCESSING'")
  int complete(@Param("subject") String subject, @Param("key") String key, @Param("response") String response,
      @Param("now") LocalDateTime now);

  @Update("UPDATE payment_idempotency_record SET status = 'PROCESSING', lease_expire_time = #{lease}, update_time = #{now} WHERE subject = #{subject} AND idempotency_key = #{key} AND status IN ('PROCESSING', 'FAILED') AND lease_expire_time < #{now}")
  int takeOver(@Param("subject") String subject, @Param("key") String key, @Param("lease") LocalDateTime lease,
      @Param("now") LocalDateTime now);

  @Update("UPDATE payment_idempotency_record SET status = 'FAILED', lease_expire_time = #{now}, update_time = #{now} WHERE subject = #{subject} AND idempotency_key = #{key} AND status = 'PROCESSING'")
  int fail(@Param("subject") String subject, @Param("key") String key, @Param("now") LocalDateTime now);
}
