package com.mealflow.authuser.api;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(@NotBlank String phone, String code) {
}
