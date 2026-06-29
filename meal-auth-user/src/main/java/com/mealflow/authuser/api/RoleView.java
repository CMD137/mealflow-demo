package com.mealflow.authuser.api;

import java.util.List;

public record RoleView(String roleCode, String roleName, String description, boolean builtin,
    List<String> permissions) {
}
