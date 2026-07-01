package com.mealflow.authuser.api;

import java.util.List;

public record SignInView(
    boolean signedToday,
    int continuousDays,
    int totalDays,
    int totalPoints,
    int todayRewardPoints,
    List<String> monthSignDates) {
}
