package com.mealflow.authuser.mapper;

import java.time.LocalDateTime;

public class SystemUserRow {
  private long id;
  private String phone;
  private String nickname;
  private String status;
  private String identitySummary;
  private LocalDateTime createTime;

  public long getId() { return id; }
  public void setId(long id) { this.id = id; }
  public String getPhone() { return phone; }
  public void setPhone(String phone) { this.phone = phone; }
  public String getNickname() { return nickname; }
  public void setNickname(String nickname) { this.nickname = nickname; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public String getIdentitySummary() { return identitySummary; }
  public void setIdentitySummary(String identitySummary) { this.identitySummary = identitySummary; }
  public LocalDateTime getCreateTime() { return createTime; }
  public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
