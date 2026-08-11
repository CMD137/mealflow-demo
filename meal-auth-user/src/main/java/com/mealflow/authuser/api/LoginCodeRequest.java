package com.mealflow.authuser.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record LoginCodeRequest(
    @NotBlank @Pattern(regexp = "^1\\d{10}$", message = "phone must be an 11 digit mobile number") String phone) {
}
