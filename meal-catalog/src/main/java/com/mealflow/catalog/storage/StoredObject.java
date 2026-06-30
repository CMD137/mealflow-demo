package com.mealflow.catalog.storage;

public record StoredObject(String url, String objectKey, String provider, long size, String contentType) {
}
