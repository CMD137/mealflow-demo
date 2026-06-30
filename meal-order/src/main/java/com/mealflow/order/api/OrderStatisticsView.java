package com.mealflow.order.api;

public record OrderStatisticsView(long totalCount, long waitingAcceptCount, long acceptedCount,
    long deliveringCount, long completedCount, long cancelledCount, int turnoverCent) {
}
