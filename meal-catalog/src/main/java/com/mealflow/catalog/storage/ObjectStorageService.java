package com.mealflow.catalog.storage;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface ObjectStorageService {
  StoredObject upload(long merchantId, MultipartFile file);

  Resource load(String objectKey);

  String contentType(String objectKey);
}
