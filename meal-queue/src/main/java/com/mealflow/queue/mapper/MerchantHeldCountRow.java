package com.mealflow.queue.mapper;

public class MerchantHeldCountRow {
  private long merchantId;
  private int heldCount;

  public long getMerchantId() {
    return merchantId;
  }

  public void setMerchantId(long merchantId) {
    this.merchantId = merchantId;
  }

  public int getHeldCount() {
    return heldCount;
  }

  public void setHeldCount(int heldCount) {
    this.heldCount = heldCount;
  }
}
