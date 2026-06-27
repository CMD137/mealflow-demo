package com.mealflow.app.payment;

public record PaymentView(long payOrderId, long orderId, int amountCent, String status) {
}
