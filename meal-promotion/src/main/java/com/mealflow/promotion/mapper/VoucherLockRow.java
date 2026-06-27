package com.mealflow.promotion.mapper;

public class VoucherLockRow {
  private long id;
  private long userVoucherId;
  private String status;
  private Long ticketId;
  private Long orderId;

  public long getId() {
    return id;
  }

  public void setId(long id) {
    this.id = id;
  }

  public long getUserVoucherId() {
    return userVoucherId;
  }

  public void setUserVoucherId(long userVoucherId) {
    this.userVoucherId = userVoucherId;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public Long getTicketId() {
    return ticketId;
  }

  public void setTicketId(Long ticketId) {
    this.ticketId = ticketId;
  }

  public Long getOrderId() {
    return orderId;
  }

  public void setOrderId(Long orderId) {
    this.orderId = orderId;
  }
}
