package com.mealflow.notify.mapper;

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
public interface NotifyMapper {
  @Select("SELECT COALESCE(MAX(id), 10000) FROM notify_message")
  long maxMessageId();

  @Select("SELECT COALESCE(MAX(id), 10000) FROM notify_delivery")
  long maxDeliveryId();

  @Insert("""
      INSERT INTO notify_message (id, user_id, biz_type, content, create_time)
      VALUES (#{id}, #{userId}, #{bizType}, #{content}, #{createTime})
      """)
  int insert(@Param("id") long id, @Param("userId") long userId, @Param("bizType") String bizType,
      @Param("content") String content, @Param("createTime") LocalDateTime createTime);

  @Select("""
      SELECT template_code, biz_type, content_template, channels, enabled
      FROM notify_template
      WHERE template_code = #{templateCode}
      """)
  @Results(id = "templateMap", value = {
      @Result(column = "template_code", property = "templateCode"),
      @Result(column = "biz_type", property = "bizType"),
      @Result(column = "content_template", property = "contentTemplate"),
      @Result(column = "channels", property = "channels"),
      @Result(column = "enabled", property = "enabled")
  })
  NotifyTemplateRow findTemplate(String templateCode);

  @Insert("""
      INSERT INTO notify_delivery (
        id, message_id, user_id, channel, target, status, content, create_time, update_time
      )
      VALUES (
        #{id}, #{messageId}, #{userId}, #{channel}, #{target}, #{status}, #{content}, #{now}, #{now}
      )
      """)
  int insertDelivery(@Param("id") long id, @Param("messageId") long messageId, @Param("userId") long userId,
      @Param("channel") String channel, @Param("target") String target, @Param("status") String status,
      @Param("content") String content, @Param("now") LocalDateTime now);

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

  @Select("""
      SELECT id, user_id, biz_type, content, create_time
      FROM notify_message
      ORDER BY create_time DESC, id DESC
      """)
  @ResultMap("messageMap")
  List<NotifyMessageRow> findAllMessages();

  @Select("""
      SELECT id, message_id, user_id, channel, target, status, content, create_time
      FROM notify_delivery
      WHERE user_id = #{userId}
      ORDER BY create_time DESC, id DESC
      """)
  @Results(id = "deliveryMap", value = {
      @Result(column = "id", property = "id"),
      @Result(column = "message_id", property = "messageId"),
      @Result(column = "user_id", property = "userId"),
      @Result(column = "channel", property = "channel"),
      @Result(column = "target", property = "target"),
      @Result(column = "status", property = "status"),
      @Result(column = "content", property = "content"),
      @Result(column = "create_time", property = "createTime")
  })
  List<NotifyDeliveryRow> findDeliveriesByUser(long userId);

  @Select("""
      SELECT id, message_id, user_id, channel, target, status, content, create_time
      FROM notify_delivery
      ORDER BY create_time DESC, id DESC
      """)
  @ResultMap("deliveryMap")
  List<NotifyDeliveryRow> findAllDeliveries();
}
