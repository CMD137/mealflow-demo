package com.mealflow.catalog.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mealflow.storage")
public class ObjectStorageProperties {
  private String provider = "local";
  private String localDir = "uploads/catalog";
  private String publicBaseUrl = "";
  private String endpoint = "";
  private String bucket = "";
  private String accessKeyId = "";
  private String accessKeySecret = "";
  private String objectPrefix = "catalog";
  private long maxSizeBytes = 5 * 1024 * 1024L;

  public String getProvider() {
    return provider;
  }

  public void setProvider(String provider) {
    this.provider = provider;
  }

  public String getLocalDir() {
    return localDir;
  }

  public void setLocalDir(String localDir) {
    this.localDir = localDir;
  }

  public String getPublicBaseUrl() {
    return publicBaseUrl;
  }

  public void setPublicBaseUrl(String publicBaseUrl) {
    this.publicBaseUrl = publicBaseUrl;
  }

  public String getEndpoint() {
    return endpoint;
  }

  public void setEndpoint(String endpoint) {
    this.endpoint = endpoint;
  }

  public String getBucket() {
    return bucket;
  }

  public void setBucket(String bucket) {
    this.bucket = bucket;
  }

  public String getAccessKeyId() {
    return accessKeyId;
  }

  public void setAccessKeyId(String accessKeyId) {
    this.accessKeyId = accessKeyId;
  }

  public String getAccessKeySecret() {
    return accessKeySecret;
  }

  public void setAccessKeySecret(String accessKeySecret) {
    this.accessKeySecret = accessKeySecret;
  }

  public String getObjectPrefix() {
    return objectPrefix;
  }

  public void setObjectPrefix(String objectPrefix) {
    this.objectPrefix = objectPrefix;
  }

  public long getMaxSizeBytes() {
    return maxSizeBytes;
  }

  public void setMaxSizeBytes(long maxSizeBytes) {
    this.maxSizeBytes = maxSizeBytes;
  }
}
