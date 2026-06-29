package com.mealflow.authuser.api;

import jakarta.validation.constraints.NotBlank;

public record AddressRequest(
    @NotBlank String contactName,
    @NotBlank String phone,
    @NotBlank String detail
) {
}
