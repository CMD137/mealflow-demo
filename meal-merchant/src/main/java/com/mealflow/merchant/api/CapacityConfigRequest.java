package com.mealflow.merchant.api;

import jakarta.validation.constraints.Min;

public record CapacityConfigRequest(@Min(1) int baseCapacity, double manualFactor) {
}
