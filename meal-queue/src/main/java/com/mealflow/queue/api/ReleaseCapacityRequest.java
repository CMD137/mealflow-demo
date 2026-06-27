package com.mealflow.queue.api;

import jakarta.validation.constraints.NotBlank;

public record ReleaseCapacityRequest(@NotBlank String requestId, @NotBlank String reason) {
}
