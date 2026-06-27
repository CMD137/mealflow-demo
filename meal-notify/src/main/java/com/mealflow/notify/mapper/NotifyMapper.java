package com.mealflow.notify.mapper;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface NotifyMapper {
  @Insert("""
      INSERT INTO notify_message (id, user_id, biz_type, content, create_time)
      VALUES (#{id}, #{userId}, #{bizType}, #{content}, #{createTime})
      """)
  int insert(@Param("id") long id, @Param("userId") long userId, @Param("bizType") String bizType,
      @Param("content") String content, @Param("createTime") LocalDateTime createTime);

  @Select("""
      SELECT id, user_id, biz_type, content, create_time
      FROM notify_message
      WHERE user_id = #{userId}
      ORDER BY create_time DESC
      """)
  @Results(id = "messageMap", value = {
      @Result(column = "id", property = "id"),
      @Result(column = "user_id", property = "userId"),
      @Result(column = "biz_type", property = "bizType"),
      @Result(column = "content", property = "content"),
      @Result(column = "create_time", property = "createTime")
  })
  List<NotifyMessageRow> findByUser(long userId);
}
