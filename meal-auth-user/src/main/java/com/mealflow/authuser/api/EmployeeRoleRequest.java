package com.mealflow.authuser.api;

import jakarta.validation.constraints.NotBlank;

public record EmployeeRoleRequest(@NotBlank String roleCode) {
}
