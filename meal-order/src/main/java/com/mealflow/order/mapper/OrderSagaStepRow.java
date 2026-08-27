package com.mealflow.order.mapper;

import java.time.LocalDateTime;

public class OrderSagaStepRow {
  private long id;
  private long orderId;
  private long payOrderId;
  private String sagaType;
  private String stepName;
  private int stepOrder;
  private String reason;
  private String status;
  private int retryCount;
  private LocalDateTime nextRetryTime;
  private LocalDateTime leaseUntil;
  private String lastError;

  public long getId() { return id; }
  public void setId(long id) { this.id = id; }
  public long getOrderId() { return orderId; }
  public void setOrderId(long orderId) { this.orderId = orderId; }
  public long getPayOrderId() { return payOrderId; }
  public void setPayOrderId(long payOrderId) { this.payOrderId = payOrderId; }
  public String getSagaType() { return sagaType; }
  public void setSagaType(String sagaType) { this.sagaType = sagaType; }
  public String getStepName() { return stepName; }
  public void setStepName(String stepName) { this.stepName = stepName; }
  public int getStepOrder() { return stepOrder; }
  public void setStepOrder(int stepOrder) { this.stepOrder = stepOrder; }
  public String getReason() { return reason; }
  public void setReason(String reason) { this.reason = reason; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public int getRetryCount() { return retryCount; }
  public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
  public LocalDateTime getNextRetryTime() { return nextRetryTime; }
  public void setNextRetryTime(LocalDateTime nextRetryTime) { this.nextRetryTime = nextRetryTime; }
  public LocalDateTime getLeaseUntil() { return leaseUntil; }
  public void setLeaseUntil(LocalDateTime leaseUntil) { this.leaseUntil = leaseUntil; }
  public String getLastError() { return lastError; }
  public void setLastError(String lastError) { this.lastError = lastError; }
}
