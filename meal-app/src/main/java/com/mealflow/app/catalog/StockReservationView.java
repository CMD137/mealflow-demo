package com.mealflow.app.catalog;

public record StockReservationView(
    long reservationId,
    long skuId,
    int quantity,
    String status,
    Long ticketId,
    Long orderId
) {
}
