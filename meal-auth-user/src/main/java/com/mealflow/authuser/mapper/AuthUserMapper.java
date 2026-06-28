package com.mealflow.authuser.mapper;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AuthUserMapper {
  @Select("SELECT COALESCE(MAX(id), 10000) FROM user_account")
  long maxUserId();

  @Select("SELECT id, phone, nickname, status FROM user_account WHERE id = #{id}")
  @Results(id = "userMap", value = {
      @Result(column = "id", property = "id"),
      @Result(column = "phone", property = "phone"),
      @Result(column = "nickname", property = "nickname"),
      @Result(column = "status", property = "status")
  })
  UserAccountRow findUser(long id);

  @Select("SELECT id, phone, nickname, status FROM user_account WHERE phone = #{phone}")
  @ResultMap("userMap")
  UserAccountRow findUserByPhone(String phone);

  @Insert("""
      INSERT INTO user_account (id, phone, nickname, status, create_time, update_time)
      VALUES (#{id}, #{phone}, #{nickname}, #{status}, #{now}, #{now})
      """)
  int insertUser(@Param("id") long id, @Param("phone") String phone, @Param("nickname") String nickname,
      @Param("status") String status, @Param("now") LocalDateTime now);

  @Select("""
      SELECT id, user_id, contact_name, contact_phone, detail
      FROM user_address
      WHERE user_id = #{userId}
      ORDER BY id
      """)
  @Results(id = "addressMap", value = {
      @Result(column = "id", property = "id"),
      @Result(column = "user_id", property = "userId"),
      @Result(column = "contact_name", property = "contactName"),
      @Result(column = "contact_phone", property = "contactPhone"),
      @Result(column = "detail", property = "detail")
  })
  List<UserAddressRow> findAddresses(long userId);
}
