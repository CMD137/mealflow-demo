package com.mealflow.catalog.storage;

import com.mealflow.common.api.ErrorCode;
import com.mealflow.common.exception.BizException;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ObjectStorageProperties.class)
public class ObjectStorageConfiguration {
  @Bean
  public ObjectStorageService objectStorageService(ObjectStorageProperties properties) {
    String provider = properties.getProvider() == null ? "local" : properties.getProvider().trim();
    if ("local".equalsIgnoreCase(provider)) {
      return new LocalObjectStorageService(properties);
    }
    if ("aliyun-oss".equalsIgnoreCase(provider)) {
      return new AliyunOssObjectStorageService(properties);
    }
    throw new BizException(ErrorCode.BAD_REQUEST, "Unsupported storage provider: " + provider);
  }
}
