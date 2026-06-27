package com.mealflow.promotion.mapper;

public class UserVoucherRow {
  private long id;
  private long userId;
  private long voucherId;
  private String status;

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
}
