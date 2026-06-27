package com.mealflow.queue.mapper;

import java.time.LocalDateTime;

public class CapacityTokenRow {
  private long id;
  private String requestId;
  private long merchantId;
  private Long ticketId;
  private Long orderId;
  private String status;
  private LocalDateTime expireTime;
  private String releaseReason;

  public long getId() {
    return id;
  }

  public void setId(long id) {
    this.id = id;
  }

  public String getRequestId() {
    return requestId;
  }

  public void setRequestId(String requestId) {
    this.requestId = requestId;
  }

  public long getMerchantId() {
    return merchantId;
  }

  public void setMerchantId(long merchantId) {
    this.merchantId = merchantId;
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

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public LocalDateTime getExpireTime() {
    return expireTime;
  }

  public void setExpireTime(LocalDateTime expireTime) {
    this.expireTime = expireTime;
  }

  public String getReleaseReason() {
    return releaseReason;
  }

  public void setReleaseReason(String releaseReason) {
    this.releaseReason = releaseReason;
  }
}
