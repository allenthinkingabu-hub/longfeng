package com.longfeng.fileservice.provider;

import java.io.InputStream;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 阿里云 OSS 实现 · Q-B 决策：本 Phase 仅 stub · AK/SK vault 接入留 S10.
 *
 * <p>激活条件 {@code file-service.storage.provider=oss}。本类所有方法抛
 * UnsupportedOperationException · 让调用方感知需切回 minio 本地 / 等 S10 落地。
 */
@Component
@ConditionalOnProperty(name = "file-service.storage.provider", havingValue = "oss")
public class OssProvider implements StorageProvider {

  @Override
  public PresignResult presignUpload(String bucket, String objectKey, String mime, Duration ttl) {
    throw unsupported();
  }

  @Override
  public String presignDownload(String bucket, String objectKey, Duration ttl) {
    throw unsupported();
  }

  @Override
  public InputStream readObject(String bucket, String objectKey) {
    throw unsupported();
  }

  @Override
  public void putObject(String bucket, String objectKey, InputStream content, long size, String mime) {
    throw unsupported();
  }

  @Override
  public void deleteObject(String bucket, String objectKey) {
    throw unsupported();
  }

  @Override
  public String name() {
    return "oss";
  }

  private static UnsupportedOperationException unsupported() {
    return new UnsupportedOperationException(
        "OssProvider 未实现（Q-B 决策 · 本 Phase 仅 MinIO · AK/SK vault 接入留 S10）· "
            + "切回 FEATURE_STORAGE_PROVIDER=minio 或等 S10");
  }
}
