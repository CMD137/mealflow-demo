package com.mealflow.payment.api;

public record PaymentCheckoutView(long payOrderId, String provider, String checkoutUrl) {
}
