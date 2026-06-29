package com.mealflow.authuser.api;

import jakarta.validation.constraints.NotBlank;

public record EmployeeStatusRequest(@NotBlank String status) {
}
