package com.kiments.auth.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.storage.profiles")
public class StorageProperties {
  private String path = "storage/profiles";
  private long maxSizeBytes = 2 * 1024 * 1024; // 2 MB
  private int maxDimension = 4096;

  public String getPath() {
    return path;
  }

  public void setPath(String path) {
    this.path = path;
  }

  public long getMaxSizeBytes() {
    return maxSizeBytes;
  }

  public void setMaxSizeBytes(long maxSizeBytes) {
    this.maxSizeBytes = maxSizeBytes;
  }

  public int getMaxDimension() {
    return maxDimension;
  }

  public void setMaxDimension(int maxDimension) {
    this.maxDimension = maxDimension;
  }
}
