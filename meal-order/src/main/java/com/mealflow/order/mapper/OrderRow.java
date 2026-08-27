package com.mealflow.order.mapper;

import java.time.LocalDateTime;

public class OrderRow {
  private long id;
  private long userId;
  private long merchantId;
  private String status;
  private Long queueTicketId;
  private long capacityTokenId;
  private long payOrderId;
  private String reservationIdsJson;
  private Long voucherLockId;
  private String itemsJson;
  private int amountCent;
  private String contactName;
  private String contactPhone;
  private String deliveryAddress;
  private LocalDateTime paymentExpireTime;

  public long getId() {
    return id;
  }

  public void setId(long id) {
    this.id = id;
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

  public Long getQueueTicketId() {
    return queueTicketId;
  }

  public void setQueueTicketId(Long queueTicketId) {
    this.queueTicketId = queueTicketId;
  }

  public long getCapacityTokenId() {
    return capacityTokenId;
  }

  public void setCapacityTokenId(long capacityTokenId) {
    this.capacityTokenId = capacityTokenId;
  }

  public long getPayOrderId() {
    return payOrderId;
  }

  public void setPayOrderId(long payOrderId) {
    this.payOrderId = payOrderId;
  }

  public String getReservationIdsJson() {
    return reservationIdsJson;
  }

  public void setReservationIdsJson(String reservationIdsJson) {
    this.reservationIdsJson = reservationIdsJson;
  }

  public Long getVoucherLockId() {
    return voucherLockId;
  }

  public void setVoucherLockId(Long voucherLockId) {
    this.voucherLockId = voucherLockId;
  }

  public String getItemsJson() {
    return itemsJson;
  }

  public void setItemsJson(String itemsJson) {
    this.itemsJson = itemsJson;
  }

  public int getAmountCent() {
    return amountCent;
  }

  public void setAmountCent(int amountCent) {
    this.amountCent = amountCent;
  }

  public String getContactName() { return contactName; }
  public void setContactName(String contactName) { this.contactName = contactName; }
  public String getContactPhone() { return contactPhone; }
  public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }
  public String getDeliveryAddress() { return deliveryAddress; }
  public void setDeliveryAddress(String deliveryAddress) { this.deliveryAddress = deliveryAddress; }
  public LocalDateTime getPaymentExpireTime() { return paymentExpireTime; }
  public void setPaymentExpireTime(LocalDateTime paymentExpireTime) { this.paymentExpireTime = paymentExpireTime; }
}
