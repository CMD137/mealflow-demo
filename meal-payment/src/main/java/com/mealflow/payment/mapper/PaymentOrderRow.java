package com.mealflow.payment.mapper;

public class PaymentOrderRow {
  private long id;
  private long orderId;
  private int amountCent;
  private String status;

  public long getId() {
    return id;
  }

  public void setId(long id) {
    this.id = id;
  }

  public long getOrderId() {
    return orderId;
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
}
