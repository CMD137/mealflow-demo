package com.mealflow.authuser.otp;

public interface OtpPort {
  void issueLoginCode(String phone);

  void verifyLoginCode(String phone, String code);
}
