package com.mealflow.promotion.mapper;

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
public interface PromotionMapper {
  @Select("SELECT COALESCE(MAX(id), 10000) FROM user_voucher")
  long maxUserVoucherId();

  @Select("SELECT COALESCE(MAX(id), 10000) FROM voucher_lock")
  long maxVoucherLockId();

  @Select("SELECT COALESCE(MAX(id), 10000) FROM voucher_claim")
  long maxVoucherClaimId();

  @Select("SELECT COALESCE(MAX(id), 10000) FROM voucher")
  long maxVoucherId();

  @Select("""
      SELECT COUNT(*)
      FROM INFORMATION_SCHEMA.COLUMNS
      WHERE UPPER(TABLE_NAME) = UPPER('voucher') AND UPPER(COLUMN_NAME) = UPPER(#{columnName})
      """)
  int countVoucherColumn(String columnName);

  @Update("ALTER TABLE voucher ADD COLUMN name VARCHAR(128) NOT NULL DEFAULT ''")
  int addVoucherNameColumn();

  @Update("ALTER TABLE voucher ADD COLUMN type VARCHAR(32) NOT NULL DEFAULT 'SECKILL'")
  int addVoucherTypeColumn();

  @Update("ALTER TABLE voucher ADD COLUMN status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE'")
  int addVoucherStatusColumn();

  @Update("ALTER TABLE voucher ADD COLUMN start_time TIMESTAMP NULL")
  int addVoucherStartTimeColumn();

  @Update("ALTER TABLE voucher ADD COLUMN end_time TIMESTAMP NULL")
  int addVoucherEndTimeColumn();

  @Update("ALTER TABLE voucher ADD COLUMN create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP")
  int addVoucherCreateTimeColumn();

  @Update("ALTER TABLE voucher ADD COLUMN update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP")
  int addVoucherUpdateTimeColumn();

  @Update("""
      UPDATE voucher
      SET name = CASE WHEN name = '' THEN '午高峰秒杀券' ELSE name END,
          type = CASE WHEN type = '' THEN 'SECKILL' ELSE type END,
          status = CASE WHEN status = '' THEN 'ACTIVE' ELSE status END,
          update_time = CURRENT_TIMESTAMP
      WHERE id = 1000
      """)
  int hydrateSeedVoucherMetadata();

  @Update("""
      CREATE TABLE IF NOT EXISTS voucher_claim_retry (
        id BIGINT PRIMARY KEY,
        user_id BIGINT NOT NULL,
        voucher_id BIGINT NOT NULL,
        status VARCHAR(32) NOT NULL,
        retry_count INT NOT NULL DEFAULT 0,
        max_retries INT NOT NULL DEFAULT 3,
        last_error VARCHAR(512) NULL,
        next_retry_time TIMESTAMP NOT NULL,
        create_time TIMESTAMP NOT NULL,
        update_time TIMESTAMP NOT NULL,
        UNIQUE KEY uk_voucher_claim_retry_user_voucher (user_id, voucher_id),
        INDEX idx_voucher_claim_retry_status_time (status, next_retry_time)
      )
      """)
  int createClaimRetryTable();

  @Select("SELECT COALESCE(MAX(id), 10000) FROM voucher_claim_retry")
  long maxVoucherClaimRetryId();

  @Select("SELECT COUNT(*) FROM voucher_claim_retry WHERE status = #{status}")
  long countClaimRetryByStatus(String status);

  @Select("SELECT id, name, type, discount_cent, stock, status FROM voucher WHERE id = #{id}")
  @Results(id = "voucherMap", value = {
      @Result(column = "id", property = "id"),
      @Result(column = "name", property = "name"),
      @Result(column = "type", property = "type"),
      @Result(column = "discount_cent", property = "discountCent"),
      @Result(column = "stock", property = "stock"),
      @Result(column = "status", property = "status")
  })
  VoucherRow findVoucher(long id);

  @Select("SELECT id, name, type, discount_cent, stock, status FROM voucher ORDER BY id")
  @ResultMap("voucherMap")
  List<VoucherRow> findVouchers();

  @Insert("""
      INSERT INTO voucher (id, name, type, discount_cent, stock, status, create_time, update_time)
      VALUES (#{id}, #{name}, #{type}, #{discountCent}, #{stock}, #{status}, #{now}, #{now})
      """)
  int insertVoucher(@Param("id") long id, @Param("name") String name, @Param("type") String type,
      @Param("discountCent") int discountCent, @Param("stock") int stock, @Param("status") String status,
      @Param("now") LocalDateTime now);

  @Update("""
      UPDATE voucher
      SET name = #{name}, type = #{type}, discount_cent = #{discountCent}, stock = #{stock},
          status = #{status}, update_time = #{now}
      WHERE id = #{id}
      """)
  int updateVoucher(@Param("id") long id, @Param("name") String name, @Param("type") String type,
      @Param("discountCent") int discountCent, @Param("stock") int stock, @Param("status") String status,
      @Param("now") LocalDateTime now);

  @Update("UPDATE voucher SET stock = stock - 1 WHERE id = #{id} AND stock > 0")
  int decrementStock(long id);

  @Update("UPDATE voucher SET stock = stock + 1 WHERE id = #{id}")
  int incrementStock(long id);

  @Select("SELECT COUNT(*) FROM user_voucher WHERE user_id = #{userId} AND voucher_id = #{voucherId}")
  int countUserVoucher(@Param("userId") long userId, @Param("voucherId") long voucherId);

  @Insert("""
      INSERT INTO user_voucher (id, user_id, voucher_id, status, create_time, update_time)
      VALUES (#{id}, #{userId}, #{voucherId}, #{status}, #{now}, #{now})
      """)
  int insertUserVoucher(@Param("id") long id, @Param("userId") long userId, @Param("voucherId") long voucherId,
      @Param("status") String status, @Param("now") LocalDateTime now);

  @Select("SELECT id, user_id, voucher_id, status FROM user_voucher WHERE id = #{id}")
  @Results(id = "userVoucherMap", value = {
      @Result(column = "id", property = "id"),
      @Result(column = "user_id", property = "userId"),
      @Result(column = "voucher_id", property = "voucherId"),
      @Result(column = "status", property = "status")
  })
  UserVoucherRow findUserVoucher(long id);

  @Update("""
      UPDATE user_voucher
      SET status = #{status}, update_time = #{now}
      WHERE id = #{id} AND status = #{expectedStatus}
      """)
  int updateUserVoucherStatusIfCurrent(@Param("id") long id, @Param("status") String status,
      @Param("expectedStatus") String expectedStatus, @Param("now") LocalDateTime now);

  @Update("""
      UPDATE user_voucher
      SET status = #{status}, update_time = #{now}
      WHERE id = #{id}
      """)
  int updateUserVoucherStatus(@Param("id") long id, @Param("status") String status,
      @Param("now") LocalDateTime now);

  @Insert("""
      INSERT INTO voucher_claim (id, user_id, voucher_id, status, create_time, update_time)
      VALUES (#{id}, #{userId}, #{voucherId}, #{status}, #{now}, #{now})
      """)
  int insertClaim(@Param("id") long id, @Param("userId") long userId, @Param("voucherId") long voucherId,
      @Param("status") String status, @Param("now") LocalDateTime now);

  @Select("SELECT id, user_id, voucher_id, status FROM voucher_claim ORDER BY id")
  @Results(id = "claimMap", value = {
      @Result(column = "id", property = "id"),
      @Result(column = "user_id", property = "userId"),
      @Result(column = "voucher_id", property = "voucherId"),
      @Result(column = "status", property = "status")
  })
  List<VoucherClaimRow> findClaims();

  @Insert("""
      INSERT INTO voucher_claim_retry (
        id, user_id, voucher_id, status, retry_count, max_retries, last_error,
        next_retry_time, create_time, update_time
      )
      VALUES (
        #{id}, #{userId}, #{voucherId}, #{status}, #{retryCount}, #{maxRetries}, #{lastError},
        #{nextRetryTime}, #{now}, #{now}
      )
      """)
  int insertClaimRetry(@Param("id") long id, @Param("userId") long userId, @Param("voucherId") long voucherId,
      @Param("status") String status, @Param("retryCount") int retryCount, @Param("maxRetries") int maxRetries,
      @Param("lastError") String lastError, @Param("nextRetryTime") LocalDateTime nextRetryTime,
      @Param("now") LocalDateTime now);

  @Update("""
      UPDATE voucher_claim_retry
      SET status = #{status}, last_error = #{lastError}, next_retry_time = #{nextRetryTime}, update_time = #{now}
      WHERE user_id = #{userId} AND voucher_id = #{voucherId} AND status <> 'REPAIRED'
      """)
  int touchClaimRetry(@Param("userId") long userId, @Param("voucherId") long voucherId,
      @Param("status") String status, @Param("lastError") String lastError,
      @Param("nextRetryTime") LocalDateTime nextRetryTime, @Param("now") LocalDateTime now);

  @Update("""
      UPDATE voucher_claim_retry
      SET status = #{status}, retry_count = #{retryCount}, last_error = #{lastError},
        next_retry_time = #{nextRetryTime}, update_time = #{now}
      WHERE id = #{id}
      """)
  int updateClaimRetry(@Param("id") long id, @Param("status") String status,
      @Param("retryCount") int retryCount, @Param("lastError") String lastError,
      @Param("nextRetryTime") LocalDateTime nextRetryTime, @Param("now") LocalDateTime now);

  @Select("""
      SELECT id, user_id, voucher_id, status, retry_count, max_retries, last_error, next_retry_time
      FROM voucher_claim_retry
      WHERE user_id = #{userId} AND voucher_id = #{voucherId}
      """)
  @Results(id = "claimRetryMap", value = {
      @Result(column = "id", property = "id"),
      @Result(column = "user_id", property = "userId"),
      @Result(column = "voucher_id", property = "voucherId"),
      @Result(column = "status", property = "status"),
      @Result(column = "retry_count", property = "retryCount"),
      @Result(column = "max_retries", property = "maxRetries"),
      @Result(column = "last_error", property = "lastError"),
      @Result(column = "next_retry_time", property = "nextRetryTime")
  })
  VoucherClaimRetryRow findClaimRetry(@Param("userId") long userId, @Param("voucherId") long voucherId);

  @Select("""
      SELECT id, user_id, voucher_id, status, retry_count, max_retries, last_error, next_retry_time
      FROM voucher_claim_retry
      ORDER BY id
      """)
  @ResultMap("claimRetryMap")
  List<VoucherClaimRetryRow> findClaimRetries();

  @Select("""
      SELECT id, user_id, voucher_id, status, retry_count, max_retries, last_error, next_retry_time
      FROM voucher_claim_retry
      WHERE status IN ('PENDING', 'RETRY') AND next_retry_time <= #{now}
      ORDER BY id
      LIMIT #{limit}
      """)
  @ResultMap("claimRetryMap")
  List<VoucherClaimRetryRow> findDueClaimRetries(@Param("now") LocalDateTime now, @Param("limit") int limit);

  @Insert("""
      INSERT INTO voucher_lock (id, user_voucher_id, status, ticket_id, order_id, create_time, update_time)
      VALUES (#{id}, #{userVoucherId}, #{status}, #{ticketId}, #{orderId}, #{now}, #{now})
      """)
  int insertLock(@Param("id") long id, @Param("userVoucherId") long userVoucherId, @Param("status") String status,
      @Param("ticketId") Long ticketId, @Param("orderId") Long orderId, @Param("now") LocalDateTime now);

  @Select("SELECT id, user_voucher_id, status, ticket_id, order_id FROM voucher_lock WHERE id = #{id}")
  @Results(id = "lockMap", value = {
      @Result(column = "id", property = "id"),
      @Result(column = "user_voucher_id", property = "userVoucherId"),
      @Result(column = "status", property = "status"),
      @Result(column = "ticket_id", property = "ticketId"),
      @Result(column = "order_id", property = "orderId")
  })
  VoucherLockRow findLock(long id);

  @Update("""
      UPDATE voucher_lock
      SET status = #{status}, order_id = #{orderId}, update_time = #{now}
      WHERE id = #{id} AND status = #{expectedStatus}
      """)
  int confirmLock(@Param("id") long id, @Param("status") String status, @Param("orderId") Long orderId,
      @Param("expectedStatus") String expectedStatus, @Param("now") LocalDateTime now);

  @Update("""
      UPDATE voucher_lock
      SET status = #{status}, update_time = #{now}
      WHERE id = #{id} AND status = #{expectedStatus}
      """)
  int releaseLock(@Param("id") long id, @Param("status") String status,
      @Param("expectedStatus") String expectedStatus, @Param("now") LocalDateTime now);

  @Select("SELECT id, user_voucher_id, status, ticket_id, order_id FROM voucher_lock ORDER BY id")
  @ResultMap("lockMap")
  List<VoucherLockRow> findLocks();

  @Select("SELECT id, voucher_id, status FROM user_voucher WHERE user_id = #{userId} ORDER BY id")
  @Results(id = "walletMap", value = {
      @Result(column = "id", property = "id"),
      @Result(column = "voucher_id", property = "voucherId"),
      @Result(column = "status", property = "status")
  })
  List<UserVoucherRow> findWallet(long userId);
}
