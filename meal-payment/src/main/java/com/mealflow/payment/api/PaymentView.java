package com.mealflow.payment.api;

public record PaymentView(long payOrderId, long orderId, int amountCent, String status) {
}
