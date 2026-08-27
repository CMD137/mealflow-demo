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
    if (!hasAllowedMagicBytes(file)) {
      throw new BizException(ErrorCode.BAD_REQUEST, "Image content does not match an allowed format");
    }
  }

  /**
   * Sniffs the file signature instead of trusting the client-declared content type. Only JPEG/PNG/
   * GIF/WebP magic bytes are accepted, so a renamed executable or HTML file cannot be stored.
   */
  private boolean hasAllowedMagicBytes(MultipartFile file) {
    byte[] header = new byte[12];
    int read;
    try (var in = file.getInputStream()) {
      read = in.readNBytes(header, 0, header.length);
    } catch (java.io.IOException ex) {
      throw new BizException(ErrorCode.BAD_REQUEST, "Unable to read image content");
    }
    if (read < 4) {
      return false;
    }
    return isJpeg(header)
        || isPng(header)
        || isGif(header)
        || isWebp(header);
  }

  private boolean isJpeg(byte[] header) {
    return (header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF;
  }

  private boolean isPng(byte[] header) {
    return (header[0] & 0xFF) == 0x89 && header[1] == 'P' && header[2] == 'N' && header[3] == 'G'
        && header[4] == 0x0D && header[5] == 0x0A && header[6] == 0x1A && header[7] == 0x0A;
  }

  private boolean isGif(byte[] header) {
    return header[0] == 'G' && header[1] == 'I' && header[2] == 'F' && header[3] == '8';
  }

  private boolean isWebp(byte[] header) {
    return header.length >= 12
        && header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
        && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P';
  }
}
