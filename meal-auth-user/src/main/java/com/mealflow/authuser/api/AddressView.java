package com.mealflow.authuser.api;

public record AddressView(long addressId, long userId, String contactName, String phone, String detail) {
}
