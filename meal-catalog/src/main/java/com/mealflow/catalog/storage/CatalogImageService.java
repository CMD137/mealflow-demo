package com.mealflow.catalog.storage;

import com.mealflow.catalog.api.ImageUploadView;
import com.mealflow.common.api.ErrorCode;
import com.mealflow.common.exception.BizException;
import java.util.Set;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class CatalogImageService {
  private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
      "image/jpeg",
      "image/png",
      "image/webp",
      "image/gif");

  private final ObjectStorageProperties properties;
  private final ObjectStorageService objectStorageService;

  public CatalogImageService(ObjectStorageProperties properties, ObjectStorageService objectStorageService) {
    this.properties = properties;
    this.objectStorageService = objectStorageService;
  }

  public ImageUploadView upload(long merchantId, MultipartFile file) {
    validate(file);
    StoredObject storedObject = objectStorageService.upload(merchantId, file);
    return new ImageUploadView(storedObject.url(), storedObject.objectKey(), storedObject.provider(),
        storedObject.size(), storedObject.contentType());
  }

  public Resource load(String objectKey) {
    return objectStorageService.load(objectKey);
  }

  public String contentType(String objectKey) {
    return objectStorageService.contentType(objectKey);
  }

  private void validate(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new BizException(ErrorCode.BAD_REQUEST, "Image file is required");
    }
    if (file.getSize() > properties.getMaxSizeBytes()) {
      throw new BizException(ErrorCode.BAD_REQUEST, "Image file is too large");
    }
    String contentType = file.getContentType();
    if (!StringUtils.hasText(contentType) || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
      throw new BizException(ErrorCode.BAD_REQUEST, "Only jpeg, png, webp and gif images are allowed");
    }
  }
}
