package com.longfeng.fileservice.provider;

import com.longfeng.common.exception.BusinessException;
import com.longfeng.common.exception.ErrCode;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import io.minio.MinioClient;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * MinIO / S3-compatible implementation of {@link AttachmentStorage}.
 *
 * <p>Active when {@code app.storage.provider=minio} (dev profile).
 * TDD §0.9 D-Storage · §10.3.
 *
 * <p>C7: No image byte[] held in heap; presign/delete/promote operate only on metadata URLs and keys.
 */
@Component
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "minio")
public class MinioAttachmentStorage implements AttachmentStorage {

    private static final Logger LOG = LoggerFactory.getLogger(MinioAttachmentStorage.class);

    private final MinioClient client;
    private final String defaultBucket;

    public MinioAttachmentStorage(
            @Value("${app.storage.minio.endpoint:http://minio:9000}") String endpoint,
            @Value("${app.storage.minio.access-key:minio}") String accessKey,
            @Value("${app.storage.minio.secret-key:minio12345}") String secretKey,
            @Value("${app.storage.minio.bucket:wrongbook-dev}") String bucket) {
        this.client = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
        this.defaultBucket = bucket;
    }

    @PostConstruct
    public void ensureBucket() {
        try {
            boolean exists = client.bucketExists(
                    BucketExistsArgs.builder().bucket(defaultBucket).build());
            if (!exists) {
                client.makeBucket(MakeBucketArgs.builder().bucket(defaultBucket).build());
                LOG.info("MinioAttachmentStorage: created bucket={}", defaultBucket);
            }
        } catch (Exception e) {
            LOG.warn("MinioAttachmentStorage: ensureBucket failed bucket={} — presign will fail at call time", defaultBucket, e);
        }
    }

    @Override
    public PresignResult presign(String bucket, String objectKey, String mimeType, Duration ttl) {
        try {
            String url = client.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.PUT)
                            .bucket(bucket)
                            .object(objectKey)
                            .expiry((int) ttl.toSeconds(), TimeUnit.SECONDS)
                            .build());
            return new PresignResult(url, objectKey, ttl.toSeconds());
        } catch (Exception e) {
            LOG.error("MinioAttachmentStorage.presign failed bucket={} key={}", bucket, objectKey, e);
            throw new BusinessException(ErrCode.INTERNAL_ERROR,
                    "msgkey:file.error.presign_failed");
        }
    }

    @Override
    public String get(String bucket, String objectKey, Duration ttl) {
        try {
            return client.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucket)
                            .object(objectKey)
                            .expiry((int) ttl.toSeconds(), TimeUnit.SECONDS)
                            .build());
        } catch (Exception e) {
            LOG.error("MinioAttachmentStorage.get failed bucket={} key={}", bucket, objectKey, e);
            throw new BusinessException(ErrCode.INTERNAL_ERROR,
                    "msgkey:file.error.get_url_failed");
        }
    }

    @Override
    public void delete(String bucket, String objectKey) {
        try {
            client.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build());
            LOG.info("MinioAttachmentStorage.delete bucket={} key={}", bucket, objectKey);
        } catch (Exception e) {
            LOG.error("MinioAttachmentStorage.delete failed bucket={} key={}", bucket, objectKey, e);
            throw new BusinessException(ErrCode.INTERNAL_ERROR,
                    "msgkey:file.error.delete_failed");
        }
    }

    @Override
    public void promote(String bucket, String objectKey, String storageClass) {
        // MinIO dev does not support storage-class lifecycle commands natively.
        // Log for observability; no-op in dev.
        LOG.info("MinioAttachmentStorage.promote (no-op in dev) bucket={} key={} class={}", bucket, objectKey, storageClass);
    }

    @Override
    public String name() {
        return "minio";
    }
}
