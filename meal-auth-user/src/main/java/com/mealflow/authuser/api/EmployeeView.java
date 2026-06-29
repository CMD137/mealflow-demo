package com.mealflow.authuser.api;

public record EmployeeView(long employeeId, long merchantId, long userId, String phone, String nickname,
    String roleCode, String roleName, String status) {
}
