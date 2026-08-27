package com.mealflow.promotion.mapper;

public class VoucherClaimRow {
  private long id;
  private String eventKey;
  private long userId;
  private long voucherId;
  private Long userVoucherId;
  private String status;
  private String lastError;

  public long getId() {
    return id;
  }

  public void setId(long id) {
    this.id = id;
  }

  public String getEventKey() {
    return eventKey;
  }

  public void setEventKey(String eventKey) {
    this.eventKey = eventKey;
  }

  public long getUserId() {
    return userId;
  }

  public void setUserId(long userId) {
    this.userId = userId;
  }

  public long getVoucherId() {
    return voucherId;
  }

  public void setVoucherId(long voucherId) {
    this.voucherId = voucherId;
  }

  public Long getUserVoucherId() {
    return userVoucherId;
  }

  public void setUserVoucherId(Long userVoucherId) {
    this.userVoucherId = userVoucherId;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getLastError() {
    return lastError;
  }

  public void setLastError(String lastError) {
    this.lastError = lastError;
  }
}
