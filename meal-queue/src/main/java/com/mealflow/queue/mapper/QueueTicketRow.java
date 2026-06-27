package com.mealflow.queue.mapper;

import java.time.LocalDateTime;

public class QueueTicketRow {
  private long id;
  private String ticketNo;
  private String requestId;
  private long userId;
  private long merchantId;
  private String status;
  private long score;
  private int aheadCountSnapshot;
  private int estimatedWaitSeconds;
  private LocalDateTime expireTime;
  private String snapshotJson;
  private Long orderId;
  private LocalDateTime readyTime;
  private LocalDateTime processingTime;

  public long getId() {
    return id;
  }

  public void setId(long id) {
    this.id = id;
  }

  public String getTicketNo() {
    return ticketNo;
  }

  public void setTicketNo(String ticketNo) {
    this.ticketNo = ticketNo;
  }

  public String getRequestId() {
    return requestId;
  }

  public void setRequestId(String requestId) {
    this.requestId = requestId;
  }

  public long getUserId() {
    return userId;
  }

  public void setUserId(long userId) {
    this.userId = userId;
  }

  public long getMerchantId() {
    return merchantId;
  }

  public void setMerchantId(long merchantId) {
    this.merchantId = merchantId;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public long getScore() {
    return score;
  }

  public void setScore(long score) {
    this.score = score;
  }

  public int getAheadCountSnapshot() {
    return aheadCountSnapshot;
  }

  public void setAheadCountSnapshot(int aheadCountSnapshot) {
    this.aheadCountSnapshot = aheadCountSnapshot;
  }

  public int getEstimatedWaitSeconds() {
    return estimatedWaitSeconds;
  }

  public void setEstimatedWaitSeconds(int estimatedWaitSeconds) {
    this.estimatedWaitSeconds = estimatedWaitSeconds;
  }

  public LocalDateTime getExpireTime() {
    return expireTime;
  }

  public void setExpireTime(LocalDateTime expireTime) {
    this.expireTime = expireTime;
  }

  public String getSnapshotJson() {
    return snapshotJson;
  }

  public void setSnapshotJson(String snapshotJson) {
    this.snapshotJson = snapshotJson;
  }

  public Long getOrderId() {
    return orderId;
  }

  public void setOrderId(Long orderId) {
    this.orderId = orderId;
  }

  public LocalDateTime getReadyTime() {
    return readyTime;
  }

  public void setReadyTime(LocalDateTime readyTime) {
    this.readyTime = readyTime;
  }

  public LocalDateTime getProcessingTime() {
    return processingTime;
  }

  public void setProcessingTime(LocalDateTime processingTime) {
    this.processingTime = processingTime;
  }
}
