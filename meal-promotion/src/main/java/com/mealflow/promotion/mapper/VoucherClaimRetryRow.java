package com.mealflow.promotion.mapper;

import java.time.LocalDateTime;

public class VoucherClaimRetryRow {
  private long id;
  private long userId;
  private long voucherId;
  private String status;
  private int retryCount;
  private int maxRetries;
  private String lastError;
  private LocalDateTime nextRetryTime;

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

  public long getVoucherId() {
    return voucherId;
  }

  public void setVoucherId(long voucherId) {
    this.voucherId = voucherId;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public int getRetryCount() {
    return retryCount;
  }

  public void setRetryCount(int retryCount) {
    this.retryCount = retryCount;
  }

  public int getMaxRetries() {
    return maxRetries;
  }

  public void setMaxRetries(int maxRetries) {
    this.maxRetries = maxRetries;
  }

  public String getLastError() {
    return lastError;
  }

  public void setLastError(String lastError) {
    this.lastError = lastError;
  }

  public LocalDateTime getNextRetryTime() {
    return nextRetryTime;
  }

  public void setNextRetryTime(LocalDateTime nextRetryTime) {
    this.nextRetryTime = nextRetryTime;
  }
}
