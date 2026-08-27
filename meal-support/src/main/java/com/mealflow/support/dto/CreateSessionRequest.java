package com.mealflow.support.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateSessionRequest(@NotBlank String channel) {
}
