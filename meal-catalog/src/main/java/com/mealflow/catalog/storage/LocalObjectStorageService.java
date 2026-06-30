package com.mealflow.catalog.storage;

import com.mealflow.common.api.ErrorCode;
import com.mealflow.common.exception.BizException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

public class LocalObjectStorageService implements ObjectStorageService {
  private final ObjectStorageProperties properties;
  private final Path root;

  public LocalObjectStorageService(ObjectStorageProperties properties) {
    this.properties = properties;
    this.root = Path.of(properties.getLocalDir()).toAbsolutePath().normalize();
  }

  @Override
  public StoredObject upload(long merchantId, MultipartFile file) {
    String objectKey = StorageFileNames.imageFileName(merchantId, file);
    Path target = root.resolve(objectKey).normalize();
    if (!target.startsWith(root)) {
      throw new BizException(ErrorCode.BAD_REQUEST, "Invalid object key");
    }
    try {
      Files.createDirectories(root);
      try (InputStream inputStream = file.getInputStream()) {
        Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
      }
      return new StoredObject(publicUrl(objectKey), objectKey, "local", file.getSize(), file.getContentType());
    } catch (IOException ex) {
      throw new BizException(ErrorCode.SYSTEM_ERROR, "Failed to store uploaded image");
    }
  }

  @Override
  public Resource load(String objectKey) {
    Path target = resolveObjectKey(objectKey);
    if (!Files.isRegularFile(target)) {
      throw new BizException(ErrorCode.NOT_FOUND, "Image does not exist");
    }
    try {
      return new UrlResource(target.toUri());
    } catch (IOException ex) {
      throw new BizException(ErrorCode.SYSTEM_ERROR, "Failed to read uploaded image");
    }
  }

  @Override
  public String contentType(String objectKey) {
    Path target = resolveObjectKey(objectKey);
    try {
      String type = Files.probeContentType(target);
      return StringUtils.hasText(type) ? type : "application/octet-stream";
    } catch (IOException ex) {
      return "application/octet-stream";
    }
  }

  private Path resolveObjectKey(String objectKey) {
    if (!StringUtils.hasText(objectKey) || objectKey.contains("/") || objectKey.contains("\\")) {
      throw new BizException(ErrorCode.NOT_FOUND, "Image does not exist");
    }
    Path target = root.resolve(objectKey).normalize();
    if (!target.startsWith(root)) {
      throw new BizException(ErrorCode.NOT_FOUND, "Image does not exist");
    }
    return target;
  }

  private String publicUrl(String objectKey) {
    String baseUrl = properties.getPublicBaseUrl();
    if (!StringUtils.hasText(baseUrl)) {
      return "/catalog/images/" + objectKey;
    }
    return trimTrailingSlash(baseUrl) + "/catalog/images/" + objectKey;
  }

  private String trimTrailingSlash(String value) {
    String result = value.trim();
    while (result.endsWith("/")) {
      result = result.substring(0, result.length() - 1);
    }
    return result;
  }
}
