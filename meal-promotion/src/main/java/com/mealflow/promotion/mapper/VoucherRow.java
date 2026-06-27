package com.mealflow.promotion.mapper;

public class VoucherRow {
  private long id;
  private int discountCent;
  private int stock;

  public long getId() {
    return id;
  }

  public void setId(long id) {
    this.id = id;
  }

  public int getDiscountCent() {
    return discountCent;
  }

  public void setDiscountCent(int discountCent) {
    this.discountCent = discountCent;
  }

  public int getStock() {
    return stock;
  }

  public void setStock(int stock) {
    this.stock = stock;
  }
}
