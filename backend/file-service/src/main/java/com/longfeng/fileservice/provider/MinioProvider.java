package com.longfeng.fileservice.provider;

import com.longfeng.fileservice.config.StorageProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** MinIO / S3-compatible 实现 · FEATURE_STORAGE_PROVIDER=minio 时激活. */
@Component
@ConditionalOnProperty(name = "file-service.storage.provider", havingValue = "minio", matchIfMissing = true)
public class MinioProvider implements StorageProvider {

  private static final Logger LOG = LoggerFactory.getLogger(MinioProvider.class);

  private final StorageProperties props;
  private final MinioClient client;

  public MinioProvider(StorageProperties props) {
    this.props = props;
    this.client =
        MinioClient.builder()
            .endpoint(props.endpoint())
            .credentials(props.accessKey(), props.secretKey())
            .build();
  }

  @PostConstruct
  public void ensureBucket() {
    try {
      boolean exists =
          client.bucketExists(BucketExistsArgs.builder().bucket(props.bucket()).build());
      if (!exists) {
        client.makeBucket(MakeBucketArgs.builder().bucket(props.bucket()).build());
        LOG.info("MinIO bucket created: {}", props.bucket());
      }
    } catch (Exception e) {
      // IT 环境下 endpoint 可能未通 · fail-fast 会阻塞 Spring context 启动
      // 留 warning · presign 时再报真错
      LOG.warn("MinIO ensureBucket failed · endpoint={} bucket={}", props.endpoint(), props.bucket(), e);
    }
  }

  @Override
  public PresignResult presignUpload(String bucket, String objectKey, String mime, Duration ttl) {
    try {
      String url =
          client.getPresignedObjectUrl(
              GetPresignedObjectUrlArgs.builder()
                  .method(Method.PUT)
                  .bucket(bucket)
                  .object(objectKey)
                  .expiry((int) ttl.toSeconds(), TimeUnit.SECONDS)
                  .build());
      return new PresignResult(url, objectKey, ttl.toSeconds());
    } catch (Exception e) {
      throw new IllegalStateException("minio presignUpload failed", e);
    }
  }

  @Override
  public String presignDownload(String bucket, String objectKey, Duration ttl) {
    try {
      return client.getPresignedObjectUrl(
          GetPresignedObjectUrlArgs.builder()
              .method(Method.GET)
              .bucket(bucket)
              .object(objectKey)
              .expiry((int) ttl.toSeconds(), TimeUnit.SECONDS)
              .build());
    } catch (Exception e) {
      throw new IllegalStateException("minio presignDownload failed", e);
    }
  }

  @Override
  public InputStream readObject(String bucket, String objectKey) {
    try {
      return client.getObject(GetObjectArgs.builder().bucket(bucket).object(objectKey).build());
    } catch (Exception e) {
      throw new IllegalStateException("minio readObject failed: " + objectKey, e);
    }
  }

  @Override
  public void putObject(String bucket, String objectKey, InputStream content, long size, String mime) {
    try {
      client.putObject(
          PutObjectArgs.builder()
              .bucket(bucket)
              .object(objectKey)
              .stream(content, size, -1)
              .contentType(mime)
              .build());
    } catch (Exception e) {
      throw new IllegalStateException("minio putObject failed: " + objectKey, e);
    }
  }

  @Override
  public void deleteObject(String bucket, String objectKey) {
    try {
      client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(objectKey).build());
    } catch (Exception e) {
      throw new IllegalStateException("minio deleteObject failed: " + objectKey, e);
    }
  }

  @Override
  public String name() {
    return "minio";
  }
}
