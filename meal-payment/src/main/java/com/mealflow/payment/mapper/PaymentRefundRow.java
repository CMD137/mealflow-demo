package com.mealflow.payment.mapper;

public class PaymentRefundRow {
  private long id;
  private long payOrderId;
  private String provider;
  private String merchantOrderNo;
  private String refundRequestNo;
  private int amountCent;
  private String status;
  private int retryCount;

  public long getId() { return id; }
  public void setId(long id) { this.id = id; }
  public long getPayOrderId() { return payOrderId; }
  public void setPayOrderId(long payOrderId) { this.payOrderId = payOrderId; }
  public String getProvider() { return provider; }
  public void setProvider(String provider) { this.provider = provider; }
  public String getMerchantOrderNo() { return merchantOrderNo; }
  public void setMerchantOrderNo(String merchantOrderNo) { this.merchantOrderNo = merchantOrderNo; }
  public String getRefundRequestNo() { return refundRequestNo; }
  public void setRefundRequestNo(String refundRequestNo) { this.refundRequestNo = refundRequestNo; }
  public int getAmountCent() { return amountCent; }
  public void setAmountCent(int amountCent) { this.amountCent = amountCent; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public int getRetryCount() { return retryCount; }
  public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
}
