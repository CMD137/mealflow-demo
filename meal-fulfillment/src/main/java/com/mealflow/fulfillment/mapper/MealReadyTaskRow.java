package com.mealflow.fulfillment.mapper;

public class MealReadyTaskRow {
  private String requestId;
  private long orderId;
  private long capacityTokenId;
  private String orderJson;
  private boolean releaseDone;
  private Long readyTicketId;
  private Long readyCapacityTokenId;
  private boolean promoteDone;
  private String status;
  private int retryCount;

  public String getRequestId() { return requestId; }
  public void setRequestId(String requestId) { this.requestId = requestId; }
  public long getOrderId() { return orderId; }
  public void setOrderId(long orderId) { this.orderId = orderId; }
  public long getCapacityTokenId() { return capacityTokenId; }
  public void setCapacityTokenId(long capacityTokenId) { this.capacityTokenId = capacityTokenId; }
  public String getOrderJson() { return orderJson; }
  public void setOrderJson(String orderJson) { this.orderJson = orderJson; }
  public boolean isReleaseDone() { return releaseDone; }
  public void setReleaseDone(boolean releaseDone) { this.releaseDone = releaseDone; }
  public Long getReadyTicketId() { return readyTicketId; }
  public void setReadyTicketId(Long readyTicketId) { this.readyTicketId = readyTicketId; }
  public Long getReadyCapacityTokenId() { return readyCapacityTokenId; }
  public void setReadyCapacityTokenId(Long readyCapacityTokenId) { this.readyCapacityTokenId = readyCapacityTokenId; }
  public boolean isPromoteDone() { return promoteDone; }
  public void setPromoteDone(boolean promoteDone) { this.promoteDone = promoteDone; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public int getRetryCount() { return retryCount; }
  public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
}
