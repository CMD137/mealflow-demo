package com.mealflow.promotion.mapper;

public class VoucherRow {
  private long id;
  private String name;
  private String type;
  private int discountCent;
  private int stock;
  private String status;

  public long getId() {
    return id;
  }

  public void setId(long id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
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

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }
}
