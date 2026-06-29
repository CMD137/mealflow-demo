package com.mealflow.authuser.api;

import java.util.List;

public record LoginResponse(long userId, String token, String nickname, String roleCode, Long merchantId,
    List<String> permissions, List<MenuView> menus) {
}
