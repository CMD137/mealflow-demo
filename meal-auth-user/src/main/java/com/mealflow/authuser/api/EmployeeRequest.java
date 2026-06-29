package com.mealflow.authuser.api;

import jakarta.validation.constraints.NotBlank;

public record EmployeeRequest(
    @NotBlank String phone,
    @NotBlank String nickname,
    @NotBlank String roleCode
) {
}
