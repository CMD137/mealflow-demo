package com.mealflow.app.payment;

public record PaymentSuccessEvent(long payOrderId, long orderId) {
}
