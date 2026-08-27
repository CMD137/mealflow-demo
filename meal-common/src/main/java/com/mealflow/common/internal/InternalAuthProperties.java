package com.mealflow.common.internal;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * Per-service configuration for internal request authentication.
 *
 * <ul>
 *   <li>{@code secret} - shared HMAC secret; blank secret disables the filter (safe default).</li>
 *   <li>{@code service-name} - caller identity this service uses when signing outgoing requests.</li>
 *   <li>{@code public-paths} - regex list of endpoints reachable without a signature
 *       (ping/actuator/login/callback/public catalog).</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "mealflow.internal.auth")
public class InternalAuthProperties {

  private boolean enabled = true;
  private String secret = "";
  private String serviceName = "unknown";
  private long timestampWindowSeconds = 300;
  private int nonceCacheCapacity = 8192;
  private List<String> publicPaths = new ArrayList<>(List.of(
      "^/ping$",
      "^/[a-z0-9-]+/ping$",
      "^/actuator/.*",
      "^/auth/codes$",
      "^/auth/login$",
      "^/payments/alipay/callback$",
      "^/catalog/merchants/\\d+/(skus|categories)$",
      "^/catalog/images/.*"
  ));

  public boolean isConfigured() {
    return enabled && StringUtils.hasText(secret) && StringUtils.hasText(serviceName);
  }

  public boolean isPublicPath(String path) {
    for (String pattern : publicPaths) {
      if (path.matches(pattern)) {
        return true;
      }
    }
    return false;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getSecret() {
    return secret;
  }

  public void setSecret(String secret) {
    this.secret = secret;
  }

  public String getServiceName() {
    return serviceName;
  }

  public void setServiceName(String serviceName) {
    this.serviceName = serviceName;
  }

  public long getTimestampWindowSeconds() {
    return timestampWindowSeconds;
  }

  public void setTimestampWindowSeconds(long timestampWindowSeconds) {
    this.timestampWindowSeconds = timestampWindowSeconds;
  }

  public int getNonceCacheCapacity() {
    return nonceCacheCapacity;
  }

  public void setNonceCacheCapacity(int nonceCacheCapacity) {
    this.nonceCacheCapacity = nonceCacheCapacity;
  }

  public List<String> getPublicPaths() {
    return publicPaths;
  }

  public void setPublicPaths(List<String> publicPaths) {
    this.publicPaths = publicPaths == null ? new ArrayList<>() : new ArrayList<>(publicPaths);
  }
}
