package com.mealflow.catalog.mapper;

public class SkuRow {
  private long id;
  private long merchantId;
  private String name;
  private int priceCent;
  private int stock;

  public long getId() {
    return id;
  }

  public void setId(long id) {
    this.id = id;
  }

  public long getMerchantId() {
    return merchantId;
  }

  public void setMerchantId(long merchantId) {
    this.merchantId = merchantId;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public int getPriceCent() {
    return priceCent;
  }

  public void setPriceCent(int priceCent) {
    this.priceCent = priceCent;
  }

  public int getStock() {
    return stock;
  }

  public void setStock(int stock) {
    this.stock = stock;
  }
}
