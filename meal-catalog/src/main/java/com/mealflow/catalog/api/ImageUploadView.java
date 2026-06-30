package com.mealflow.catalog.api;

public record ImageUploadView(String url, String objectKey, String provider, long size, String contentType) {
}
