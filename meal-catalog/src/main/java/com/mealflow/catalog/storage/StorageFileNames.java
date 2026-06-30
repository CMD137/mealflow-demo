package com.mealflow.catalog.storage;

import java.util.Locale;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

final class StorageFileNames {
  private StorageFileNames() {
  }

  static String imageFileName(long merchantId, MultipartFile file) {
    return "m" + merchantId + "-" + UUID.randomUUID() + extension(file.getContentType());
  }

  static String extension(String contentType) {
    if (contentType == null) {
      return ".bin";
    }
    return switch (contentType.toLowerCase(Locale.ROOT)) {
      case "image/jpeg" -> ".jpg";
      case "image/png" -> ".png";
      case "image/webp" -> ".webp";
      case "image/gif" -> ".gif";
      default -> ".bin";
    };
  }

  static String joinPrefix(String prefix, String objectName) {
    String normalized = trimSlashes(prefix);
    if (normalized.isBlank()) {
      return objectName;
    }
    return normalized + "/" + objectName;
  }

  private static String trimSlashes(String value) {
    if (value == null) {
      return "";
    }
    String result = value.trim();
    while (result.startsWith("/")) {
      result = result.substring(1);
    }
    while (result.endsWith("/")) {
      result = result.substring(0, result.length() - 1);
    }
    return result;
  }
}
