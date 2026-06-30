package com.mealflow.catalog.api;

import jakarta.validation.constraints.NotBlank;

public record CategoryRequest(@NotBlank String name, int sortOrder, String status) {
}
