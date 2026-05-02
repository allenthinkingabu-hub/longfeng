package com.longfeng.fileservice.provider;

import com.longfeng.common.exception.BusinessException;
import com.longfeng.common.exception.ErrCode;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * AWS S3 implementation of {@link AttachmentStorage}.
 *
 * <p>Active when {@code app.storage.provider=s3} (prod-overseas profile).
 * TDD §0.9 D-Storage · §10.3.
 *
 * <p>AK/SK vault integration deferred to S10 (same as {@link ObsAttachmentStorage}).
 * Full method contract provided for compile-correctness; SDK calls wired in S10.
 *
 * <p>C7: No image byte[] held in heap.
 */
@Component
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "s3")
public class S3AttachmentStorage implements AttachmentStorage {

    private static final Logger LOG = LoggerFactory.getLogger(S3AttachmentStorage.class);

    private final String region;
    private final String bucket;
    private final String accessKey;
    private final String secretKey;

    public S3AttachmentStorage(
            @Value("${app.storage.s3.region:us-east-1}") String region,
            @Value("${app.storage.s3.bucket:wrongbook-prod-overseas}") String bucket,
            @Value("${app.storage.s3.access-key:${AWS_AK:}}") String accessKey,
            @Value("${app.storage.s3.secret-key:${AWS_SK:}}") String secretKey) {
        this.region = region;
        this.bucket = bucket;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        LOG.info("S3AttachmentStorage initialized: region={} bucket={}", region, bucket);
    }

    @Override
    public PresignResult presign(String bucket, String objectKey, String mimeType, Duration ttl) {
        // S10: Wire AWS SDK v2 S3Presigner.presignPutObject() here.
        LOG.warn("S3AttachmentStorage.presign called — S3 SDK not yet wired (S10). bucket={} key={}", bucket, objectKey);
        throw new BusinessException(ErrCode.INTERNAL_ERROR,
                "msgkey:file.error.s3_not_configured");
    }

    @Override
    public String get(String bucket, String objectKey, Duration ttl) {
        LOG.warn("S3AttachmentStorage.get called — S3 SDK not yet wired (S10). bucket={} key={}", bucket, objectKey);
        throw new BusinessException(ErrCode.INTERNAL_ERROR,
                "msgkey:file.error.s3_not_configured");
    }

    @Override
    public void delete(String bucket, String objectKey) {
        LOG.warn("S3AttachmentStorage.delete called — S3 SDK not yet wired (S10). bucket={} key={}", bucket, objectKey);
        throw new BusinessException(ErrCode.INTERNAL_ERROR,
                "msgkey:file.error.s3_not_configured");
    }

    @Override
    public void promote(String bucket, String objectKey, String storageClass) {
        // S10: Wire AWS SDK S3 CopyObject with StorageClass.
        LOG.warn("S3AttachmentStorage.promote called — S3 SDK not yet wired (S10). bucket={} key={} class={}", bucket, objectKey, storageClass);
        throw new BusinessException(ErrCode.INTERNAL_ERROR,
                "msgkey:file.error.s3_not_configured");
    }

    @Override
    public String name() {
        return "s3";
    }
}
