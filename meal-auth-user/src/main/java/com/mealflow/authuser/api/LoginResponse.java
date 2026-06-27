package com.mealflow.authuser.api;

public record LoginResponse(long userId, String token, String nickname) {
}
