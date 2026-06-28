package com.mealflow.notify.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ConsumedPushMessageRequest(
    @NotBlank String eventKey,
    @NotBlank String consumerGroup,
    @Valid @NotNull PushMessageRequest message
) {
}
