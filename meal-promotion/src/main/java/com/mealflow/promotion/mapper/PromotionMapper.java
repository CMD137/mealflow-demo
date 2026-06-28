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

  @Select("SELECT id, discount_cent, stock FROM voucher WHERE id = #{id}")
  @Results(id = "voucherMap", value = {
      @Result(column = "id", property = "id"),
      @Result(column = "discount_cent", property = "discountCent"),
      @Result(column = "stock", property = "stock")
  })
  VoucherRow findVoucher(long id);

  @Update("UPDATE voucher SET stock = stock - 1 WHERE id = #{id} AND stock > 0")
  int decrementStock(long id);

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
