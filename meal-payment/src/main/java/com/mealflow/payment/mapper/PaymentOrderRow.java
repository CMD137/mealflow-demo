package com.mealflow.payment.mapper;

import java.time.LocalDateTime;

public class PaymentOrderRow {
  private long id;
  private long orderId;
  private long userId;
  private int amountCent;
  private String status;
  private String provider;
  private String merchantOrderNo;
  private String channelTransactionNo;
  private LocalDateTime createTime;

  public long getId() {
    return id;
  }

  public void setId(long id) {
    this.id = id;
  }

  public long getOrderId() {
    return orderId;
  }

  public long getUserId() {
    return userId;
  }

  public void setUserId(long userId) {
    this.userId = userId;
  }

  public void setOrderId(long orderId) {
    this.orderId = orderId;
  }

  public int getAmountCent() {
    return amountCent;
  }

  public void setAmountCent(int amountCent) {
    this.amountCent = amountCent;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getProvider() { return provider; }
  public void setProvider(String provider) { this.provider = provider; }
  public String getMerchantOrderNo() { return merchantOrderNo; }
  public void setMerchantOrderNo(String merchantOrderNo) { this.merchantOrderNo = merchantOrderNo; }
  public String getChannelTransactionNo() { return channelTransactionNo; }
  public void setChannelTransactionNo(String channelTransactionNo) { this.channelTransactionNo = channelTransactionNo; }
  public LocalDateTime getCreateTime() { return createTime; }
  public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
