package com.mealflow.payment.api;

public record PaymentView(long payOrderId, long orderId, long userId, int amountCent, String status) {
}
