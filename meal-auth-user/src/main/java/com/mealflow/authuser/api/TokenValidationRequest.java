package com.mealflow.authuser.api;

import jakarta.validation.constraints.NotBlank;

public record TokenValidationRequest(@NotBlank String token) {
}
