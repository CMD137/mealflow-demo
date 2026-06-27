package com.mealflow.queue.mapper;

public class WaitingTicketRow {
  private long id;
  private String ticketNo;
  private long merchantId;
  private long score;

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

  public long getMerchantId() {
    return merchantId;
  }

  public void setMerchantId(long merchantId) {
    this.merchantId = merchantId;
  }

  public long getScore() {
    return score;
  }

  public void setScore(long score) {
    this.score = score;
  }
}
