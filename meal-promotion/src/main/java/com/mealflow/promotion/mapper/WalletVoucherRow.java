package com.mealflow.promotion.mapper;

public class WalletVoucherRow {
  private long id;
  private long voucherId;
  private String status;
  private String voucherName;
  private int discountCent;
  private String scope;
  private Long merchantId;

  public long getId() { return id; }
  public void setId(long id) { this.id = id; }
  public long getVoucherId() { return voucherId; }
  public void setVoucherId(long voucherId) { this.voucherId = voucherId; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public String getVoucherName() { return voucherName; }
  public void setVoucherName(String voucherName) { this.voucherName = voucherName; }
  public int getDiscountCent() { return discountCent; }
  public void setDiscountCent(int discountCent) { this.discountCent = discountCent; }
  public String getScope() { return scope; }
  public void setScope(String scope) { this.scope = scope; }
  public Long getMerchantId() { return merchantId; }
  public void setMerchantId(Long merchantId) { this.merchantId = merchantId; }
}
