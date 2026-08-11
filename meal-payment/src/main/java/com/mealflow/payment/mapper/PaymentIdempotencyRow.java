package com.mealflow.payment.mapper;

import java.time.LocalDateTime;

public class PaymentIdempotencyRow {
  private String requestHash;
  private String status;
  private String responseJson;
  private LocalDateTime leaseExpireTime;
  public String getRequestHash() { return requestHash; }
  public void setRequestHash(String requestHash) { this.requestHash = requestHash; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public String getResponseJson() { return responseJson; }
  public void setResponseJson(String responseJson) { this.responseJson = responseJson; }
  public LocalDateTime getLeaseExpireTime() { return leaseExpireTime; }
  public void setLeaseExpireTime(LocalDateTime leaseExpireTime) { this.leaseExpireTime = leaseExpireTime; }
}
