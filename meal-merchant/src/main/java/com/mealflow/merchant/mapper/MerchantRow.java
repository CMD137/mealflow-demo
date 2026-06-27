package com.mealflow.merchant.mapper;

public class MerchantRow {
  private long id;
  private String name;
  private String businessStatus;
  private int baseCapacity;
  private double manualFactor;

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

  public String getBusinessStatus() {
    return businessStatus;
  }

  public void setBusinessStatus(String businessStatus) {
    this.businessStatus = businessStatus;
  }

  public int getBaseCapacity() {
    return baseCapacity;
  }

  public void setBaseCapacity(int baseCapacity) {
    this.baseCapacity = baseCapacity;
  }

  public double getManualFactor() {
    return manualFactor;
  }

  public void setManualFactor(double manualFactor) {
    this.manualFactor = manualFactor;
  }
}
