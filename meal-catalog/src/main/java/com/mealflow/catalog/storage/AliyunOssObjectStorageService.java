package com.mealflow.catalog.storage;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PutObjectRequest;
import com.mealflow.common.api.ErrorCode;
import com.mealflow.common.exception.BizException;
import java.io.IOException;
import java.io.InputStream;
import org.springframework.core.io.Resource;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

public class AliyunOssObjectStorageService implements ObjectStorageService {
  private final ObjectStorageProperties properties;
  private final OSS ossClient;

  public AliyunOssObjectStorageService(ObjectStorageProperties properties) {
    this.properties = properties;
    requireText(properties.getEndpoint(), "ALIYUN_OSS_ENDPOINT is required");
    requireText(properties.getBucket(), "ALIYUN_OSS_BUCKET is required");
    requireText(properties.getAccessKeyId(), "ALIYUN_OSS_ACCESS_KEY_ID is required");
    requireText(properties.getAccessKeySecret(), "ALIYUN_OSS_ACCESS_KEY_SECRET is required");
    this.ossClient = new OSSClientBuilder()
        .build(properties.getEndpoint(), properties.getAccessKeyId(), properties.getAccessKeySecret());
  }

  @Override
  public StoredObject upload(long merchantId, MultipartFile file) {
    String objectKey = StorageFileNames.joinPrefix(properties.getObjectPrefix(),
        merchantId + "/" + StorageFileNames.imageFileName(merchantId, file));
    ObjectMetadata metadata = new ObjectMetadata();
    metadata.setContentLength(file.getSize());
    metadata.setContentType(file.getContentType());
    try (InputStream inputStream = file.getInputStream()) {
      ossClient.putObject(new PutObjectRequest(properties.getBucket(), objectKey, inputStream, metadata));
      return new StoredObject(publicUrl(objectKey), objectKey, "aliyun-oss", file.getSize(), file.getContentType());
    } catch (IOException ex) {
      throw new BizException(ErrorCode.SYSTEM_ERROR, "Failed to upload image to Aliyun OSS");
    }
  }

  @Override
  public Resource load(String objectKey) {
    throw new BizException(ErrorCode.NOT_FOUND, "OSS image should be accessed by returned public URL");
  }

  @Override
  public String contentType(String objectKey) {
    return "application/octet-stream";
  }

  private String publicUrl(String objectKey) {
    String baseUrl = properties.getPublicBaseUrl();
    if (StringUtils.hasText(baseUrl)) {
      return trimTrailingSlash(baseUrl) + "/" + objectKey;
    }
    String endpoint = properties.getEndpoint()
        .replace("https://", "")
        .replace("http://", "");
    return "https://" + properties.getBucket() + "." + endpoint + "/" + objectKey;
  }

  private void requireText(String value, String message) {
    if (!StringUtils.hasText(value)) {
      throw new BizException(ErrorCode.BAD_REQUEST, message);
    }
  }

  private String trimTrailingSlash(String value) {
    String result = value.trim();
    while (result.endsWith("/")) {
      result = result.substring(0, result.length() - 1);
    }
    return result;
  }
}
