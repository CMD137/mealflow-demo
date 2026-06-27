package com.mealflow.authuser.api;

public record UserView(long userId, String phone, String nickname, String level) {
}
