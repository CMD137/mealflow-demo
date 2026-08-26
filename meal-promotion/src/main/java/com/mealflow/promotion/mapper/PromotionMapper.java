package com.mealflow.promotion.mapper;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface PromotionMapper {
  @Select("""
      SELECT id, name, type, discount_cent, stock, status, start_time, end_time
      FROM voucher WHERE id = #{id}
      """)
  @Results(id = "voucherMap", value = {
      @Result(column = "id", property = "id"), @Result(column = "name", property = "name"),
      @Result(column = "type", property = "type"), @Result(column = "discount_cent", property = "discountCent"),
      @Result(column = "stock", property = "stock"), @Result(column = "status", property = "status"),
      @Result(column = "start_time", property = "startTime"), @Result(column = "end_time", property = "endTime")
  })
  VoucherRow findVoucher(long id);

  @Select("SELECT id, name, type, discount_cent, stock, status, start_time, end_time FROM voucher ORDER BY id")
  @ResultMap("voucherMap")
  List<VoucherRow> findVouchers();

  @Select("SELECT id, name, type, discount_cent, stock, status, start_time, end_time FROM voucher ORDER BY id DESC LIMIT #{limit} OFFSET #{offset}")
  @ResultMap("voucherMap")
  List<VoucherRow> findVouchersPage(@Param("limit") int limit, @Param("offset") int offset);

  @Select("SELECT COUNT(*) FROM voucher")
  long countVouchers();

  @Select("SELECT id FROM voucher ORDER BY id")
  List<Long> findVoucherIds();

  @Insert("""
      INSERT INTO voucher (name, type, discount_cent, stock, status, start_time, end_time, create_time, update_time)
      VALUES (#{name}, #{type}, #{discountCent}, #{stock}, #{status}, #{startTime}, #{endTime},
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
      """)
  @Options(useGeneratedKeys = true, keyProperty = "id")
  int insertVoucher(VoucherRow voucher);

  @Update("""
      UPDATE voucher SET name = #{name}, type = #{type}, discount_cent = #{discountCent}, stock = #{stock},
        status = #{status}, start_time = #{startTime}, end_time = #{endTime}, update_time = CURRENT_TIMESTAMP
      WHERE id = #{id}
      """)
  int updateVoucher(VoucherRow voucher);

  @Update("""
      UPDATE voucher SET stock = stock - 1, update_time = CURRENT_TIMESTAMP
      WHERE id = #{id} AND status = 'ACTIVE' AND stock > 0
      """)
  int decrementStock(long id);

  @Select("SELECT COUNT(*) FROM voucher_claim WHERE voucher_id = #{voucherId}")
  int countVoucherClaims(long voucherId);

  @Select("SELECT COUNT(*) FROM voucher_claim WHERE voucher_id = #{voucherId} AND status = #{status}")
  int countVoucherClaimsByStatus(@Param("voucherId") long voucherId, @Param("status") String status);

  @Select("SELECT COUNT(*) FROM user_voucher WHERE voucher_id = #{voucherId}")
  int countUserVouchersByVoucher(long voucherId);

  @Select("SELECT COUNT(*) FROM user_voucher WHERE user_id = #{userId} AND voucher_id = #{voucherId}")
  int countUserVoucher(@Param("userId") long userId, @Param("voucherId") long voucherId);

  @Insert("""
      INSERT INTO user_voucher (user_id, voucher_id, status, create_time, update_time)
      VALUES (#{userId}, #{voucherId}, #{status}, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
      """)
  @Options(useGeneratedKeys = true, keyProperty = "id")
  int insertUserVoucher(UserVoucherRow userVoucher);

  @Select("SELECT id, user_id, voucher_id, status FROM user_voucher WHERE id = #{id}")
  @Results(id = "userVoucherMap", value = {
      @Result(column = "id", property = "id"), @Result(column = "user_id", property = "userId"),
      @Result(column = "voucher_id", property = "voucherId"), @Result(column = "status", property = "status")
  })
  UserVoucherRow findUserVoucher(long id);

  @Update("UPDATE user_voucher SET status = #{status}, update_time = #{now} WHERE id = #{id} AND status = #{expectedStatus}")
  int updateUserVoucherStatusIfCurrent(@Param("id") long id, @Param("status") String status,
      @Param("expectedStatus") String expectedStatus, @Param("now") LocalDateTime now);

  @Update("UPDATE user_voucher SET status = #{status}, update_time = #{now} WHERE id = #{id}")
  int updateUserVoucherStatus(@Param("id") long id, @Param("status") String status, @Param("now") LocalDateTime now);

  @Insert("""
      INSERT IGNORE INTO voucher_claim (event_key, user_id, voucher_id, status, create_time, update_time)
      VALUES (#{eventKey}, #{userId}, #{voucherId}, 'PROCESSING', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
      """)
  @Options(useGeneratedKeys = true, keyProperty = "id")
  int insertClaimProcessing(VoucherClaimRow claim);

  @Select("SELECT id, event_key, user_id, voucher_id, user_voucher_id, status, last_error FROM voucher_claim WHERE event_key = #{eventKey}")
  @Results(id = "claimMap", value = {
      @Result(column = "id", property = "id"), @Result(column = "event_key", property = "eventKey"),
      @Result(column = "user_id", property = "userId"), @Result(column = "voucher_id", property = "voucherId"),
      @Result(column = "user_voucher_id", property = "userVoucherId"), @Result(column = "status", property = "status"),
      @Result(column = "last_error", property = "lastError")
  })
  VoucherClaimRow findClaimByEventKey(String eventKey);

  @Select("SELECT id, event_key, user_id, voucher_id, user_voucher_id, status, last_error FROM voucher_claim WHERE user_id = #{userId} AND voucher_id = #{voucherId}")
  @ResultMap("claimMap")
  VoucherClaimRow findClaim(@Param("userId") long userId, @Param("voucherId") long voucherId);

  @Update("""
      UPDATE voucher_claim SET status = 'CLAIMED', user_voucher_id = #{userVoucherId}, last_error = NULL,
        update_time = CURRENT_TIMESTAMP WHERE id = #{claimId} AND status = 'PROCESSING'
      """)
  int markClaimed(@Param("claimId") long claimId, @Param("userVoucherId") long userVoucherId);

  @Update("""
      UPDATE voucher_claim SET status = 'SOLD_OUT', last_error = #{lastError}, update_time = CURRENT_TIMESTAMP
      WHERE id = #{claimId} AND status = 'PROCESSING'
      """)
  int markClaimSoldOut(@Param("claimId") long claimId, @Param("lastError") String lastError);

  @Select("SELECT id, event_key, user_id, voucher_id, user_voucher_id, status, last_error FROM voucher_claim ORDER BY id")
  @ResultMap("claimMap")
  List<VoucherClaimRow> findClaims();

  @Insert("""
      INSERT INTO voucher_claim_retry
        (event_key, user_id, voucher_id, status, retry_count, last_error, next_retry_time, create_time, update_time)
      VALUES (#{eventKey}, #{userId}, #{voucherId}, #{status}, #{retryCount}, #{lastError},
        #{nextRetryTime}, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
      """)
  @Options(useGeneratedKeys = true, keyProperty = "id")
  int insertClaimRetry(VoucherClaimRetryRow retry);

  @Update("""
      UPDATE voucher_claim_retry SET status = #{status}, retry_count = #{retryCount}, last_error = #{lastError},
        next_retry_time = #{nextRetryTime}, update_time = CURRENT_TIMESTAMP WHERE event_key = #{eventKey}
      """)
  int updateClaimRetry(VoucherClaimRetryRow retry);

  @Select("SELECT id, event_key, user_id, voucher_id, status, retry_count, last_error, next_retry_time FROM voucher_claim_retry WHERE event_key = #{eventKey}")
  @Results(id = "claimRetryMap", value = {
      @Result(column = "id", property = "id"), @Result(column = "event_key", property = "eventKey"),
      @Result(column = "user_id", property = "userId"), @Result(column = "voucher_id", property = "voucherId"),
      @Result(column = "status", property = "status"), @Result(column = "retry_count", property = "retryCount"),
      @Result(column = "last_error", property = "lastError"), @Result(column = "next_retry_time", property = "nextRetryTime")
  })
  VoucherClaimRetryRow findClaimRetry(String eventKey);

  @Select("SELECT id, event_key, user_id, voucher_id, status, retry_count, last_error, next_retry_time FROM voucher_claim_retry ORDER BY id")
  @ResultMap("claimRetryMap")
  List<VoucherClaimRetryRow> findClaimRetries();

  @Select("SELECT COUNT(*) FROM voucher_claim_retry WHERE status = #{status}")
  long countClaimRetryByStatus(String status);

  @Insert("""
      INSERT INTO voucher_lock (user_voucher_id, status, ticket_id, order_id, create_time, update_time)
      VALUES (#{userVoucherId}, #{status}, #{ticketId}, #{orderId}, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
      """)
  @Options(useGeneratedKeys = true, keyProperty = "id")
  int insertLock(VoucherLockRow lock);

  @Select("SELECT id, user_voucher_id, status, ticket_id, order_id FROM voucher_lock WHERE id = #{id}")
  @Results(id = "lockMap", value = {
      @Result(column = "id", property = "id"), @Result(column = "user_voucher_id", property = "userVoucherId"),
      @Result(column = "status", property = "status"), @Result(column = "ticket_id", property = "ticketId"),
      @Result(column = "order_id", property = "orderId")
  })
  VoucherLockRow findLock(long id);

  @Select("""
      SELECT id, user_voucher_id, status, ticket_id, order_id FROM voucher_lock
      WHERE user_voucher_id = #{userVoucherId} AND status = 'LOCKED' ORDER BY id DESC LIMIT 1
      """)
  @ResultMap("lockMap")
  VoucherLockRow findActiveLockByUserVoucherId(long userVoucherId);

  @Update("UPDATE voucher_lock SET status = #{status}, order_id = #{orderId}, update_time = #{now} WHERE id = #{id} AND status = #{expectedStatus}")
  int confirmLock(@Param("id") long id, @Param("status") String status, @Param("orderId") Long orderId,
      @Param("expectedStatus") String expectedStatus, @Param("now") LocalDateTime now);

  @Update("UPDATE voucher_lock SET status = #{status}, update_time = #{now} WHERE id = #{id} AND status = #{expectedStatus}")
  int releaseLock(@Param("id") long id, @Param("status") String status,
      @Param("expectedStatus") String expectedStatus, @Param("now") LocalDateTime now);

  @Select("SELECT id, user_voucher_id, status, ticket_id, order_id FROM voucher_lock ORDER BY id")
  @ResultMap("lockMap")
  List<VoucherLockRow> findLocks();

  @Select("SELECT id, voucher_id, status FROM user_voucher WHERE user_id = #{userId} ORDER BY id")
  @Results(id = "walletMap", value = {
      @Result(column = "id", property = "id"), @Result(column = "voucher_id", property = "voucherId"),
      @Result(column = "status", property = "status")
  })
  List<UserVoucherRow> findWallet(long userId);
}
