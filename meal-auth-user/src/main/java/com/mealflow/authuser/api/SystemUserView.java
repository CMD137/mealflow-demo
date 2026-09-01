package com.mealflow.authuser.api;

import java.time.LocalDateTime;

/** Minimal user-directory projection for system governance; it intentionally excludes authentication material. */
public record SystemUserView(long userId, String phone, String nickname, String status, String identitySummary,
    LocalDateTime createTime) {
}
