package com.mealflow.authuser.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record RoleRequest(
    @NotBlank String roleCode,
    @NotBlank String roleName,
    String description,
    @NotEmpty List<String> permissions
) {
}
