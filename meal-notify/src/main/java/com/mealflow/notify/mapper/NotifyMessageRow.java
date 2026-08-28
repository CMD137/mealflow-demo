package com.mealflow.notify.mapper;

import java.time.LocalDateTime;

public class NotifyMessageRow {
  private long id;
  private long userId;
  private String recipientType;
  private long recipientId;
  private String bizType;
  private String content;
  private LocalDateTime createTime;

  public long getId() {
    return id;
  }

  public void setId(long id) {
    this.id = id;
  }

  public long getUserId() {
    return userId;
  }

  public void setUserId(long userId) {
    this.userId = userId;
  }

  public String getRecipientType() { return recipientType; }
  public void setRecipientType(String recipientType) { this.recipientType = recipientType; }
  public long getRecipientId() { return recipientId; }
  public void setRecipientId(long recipientId) { this.recipientId = recipientId; }

  public String getBizType() {
    return bizType;
  }

  public void setBizType(String bizType) {
    this.bizType = bizType;
  }

  public String getContent() {
    return content;
  }

  public void setContent(String content) {
    this.content = content;
  }

  public LocalDateTime getCreateTime() {
    return createTime;
  }

  public void setCreateTime(LocalDateTime createTime) {
    this.createTime = createTime;
  }
}
