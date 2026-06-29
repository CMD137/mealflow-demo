package com.mealflow.authuser.api;

import java.util.List;

public record TokenPrincipalView(long userId, String phone, String nickname, String roleCode, Long merchantId,
    List<String> permissions) {
}
