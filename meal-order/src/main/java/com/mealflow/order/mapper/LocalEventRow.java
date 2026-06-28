package com.mealflow.order.mapper;

import java.time.LocalDateTime;

public class LocalEventRow {
  private Long id;
  private String eventKey;
  private String eventType;
  private Integer eventVersion;
  private String aggregateType;
  private Long aggregateId;
  private String payloadJson;
  private String status;
  private Integer retryCount;
  private String lastError;
  private LocalDateTime createTime;
  private LocalDateTime updateTime;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getEventKey() {
    return eventKey;
  }

  public void setEventKey(String eventKey) {
    this.eventKey = eventKey;
  }

  public String getEventType() {
    return eventType;
  }

  public void setEventType(String eventType) {
    this.eventType = eventType;
  }

  public Integer getEventVersion() {
    return eventVersion;
  }

  public void setEventVersion(Integer eventVersion) {
    this.eventVersion = eventVersion;
  }

  public String getAggregateType() {
    return aggregateType;
  }

  public void setAggregateType(String aggregateType) {
    this.aggregateType = aggregateType;
  }

  public Long getAggregateId() {
    return aggregateId;
  }

  public void setAggregateId(Long aggregateId) {
    this.aggregateId = aggregateId;
  }

  public String getPayloadJson() {
    return payloadJson;
  }

  public void setPayloadJson(String payloadJson) {
    this.payloadJson = payloadJson;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public Integer getRetryCount() {
    return retryCount;
  }

  public void setRetryCount(Integer retryCount) {
    this.retryCount = retryCount;
  }

  public String getLastError() {
    return lastError;
  }

  public void setLastError(String lastError) {
    this.lastError = lastError;
  }

  public LocalDateTime getCreateTime() {
    return createTime;
  }

  public void setCreateTime(LocalDateTime createTime) {
    this.createTime = createTime;
  }

  public LocalDateTime getUpdateTime() {
    return updateTime;
  }

  public void setUpdateTime(LocalDateTime updateTime) {
    this.updateTime = updateTime;
  }
}
