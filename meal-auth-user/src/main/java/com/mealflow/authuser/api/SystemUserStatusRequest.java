package com.mealflow.authuser.api;

import jakarta.validation.constraints.NotBlank;

public record SystemUserStatusRequest(@NotBlank String status) {
}
