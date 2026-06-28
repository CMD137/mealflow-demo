package com.mealflow.infra.consumer;

import java.time.LocalDateTime;

public class PersistentConsumerRecordState {
  private String status;
  private LocalDateTime updateTime;

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public LocalDateTime getUpdateTime() {
    return updateTime;
  }

  public void setUpdateTime(LocalDateTime updateTime) {
    this.updateTime = updateTime;
  }
}
