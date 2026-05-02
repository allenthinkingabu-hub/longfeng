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
 * Aliyun OSS / Huawei OBS implementation of {@link AttachmentStorage}.
 *
 * <p>Active when {@code app.storage.provider=obs} (prod-cn profile).
 * TDD §0.9 D-Storage · §10.3.
 *
 * <p>In the current phase the AK/SK vault integration is deferred (D-Storage decision: "AK/SK vault 接入留 S10").
 * This implementation provides the full method contract so callers compile correctly;
 * actual OSS SDK calls will be wired in S10 when credentials are available.
 *
 * <p>C7: No image byte[] held in heap.
 */
@Component
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "obs")
public class ObsAttachmentStorage implements AttachmentStorage {

    private static final Logger LOG = LoggerFactory.getLogger(ObsAttachmentStorage.class);

    private final String endpoint;
    private final String bucket;
    private final String accessKey;
    private final String secretKey;
    private final long presignTtlMin;

    public ObsAttachmentStorage(
            @Value("${app.storage.obs.endpoint:https://obs.cn-hangzhou.aliyuncs.com}") String endpoint,
            @Value("${app.storage.obs.bucket:wrongbook-prod-cn}") String bucket,
            @Value("${app.storage.obs.access-key:${OSS_AK:}}") String accessKey,
            @Value("${app.storage.obs.secret-key:${OSS_SK:}}") String secretKey,
            @Value("${app.storage.presign-ttl-min:15}") long presignTtlMin) {
        this.endpoint = endpoint;
        this.bucket = bucket;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.presignTtlMin = presignTtlMin;
        LOG.info("ObsAttachmentStorage initialized: endpoint={} bucket={}", endpoint, bucket);
    }

    @Override
    public PresignResult presign(String bucket, String objectKey, String mimeType, Duration ttl) {
        // S10: Wire Aliyun OSS SDK presignedUrl() here.
        // Placeholder: returns a structured error so front-end knows to switch to minio.
        LOG.warn("ObsAttachmentStorage.presign called — OSS SDK not yet wired (S10). bucket={} key={}", bucket, objectKey);
        throw new BusinessException(ErrCode.INTERNAL_ERROR,
                "msgkey:file.error.obs_not_configured");
    }

    @Override
    public String get(String bucket, String objectKey, Duration ttl) {
        LOG.warn("ObsAttachmentStorage.get called — OSS SDK not yet wired (S10). bucket={} key={}", bucket, objectKey);
        throw new BusinessException(ErrCode.INTERNAL_ERROR,
                "msgkey:file.error.obs_not_configured");
    }

    @Override
    public void delete(String bucket, String objectKey) {
        LOG.warn("ObsAttachmentStorage.delete called — OSS SDK not yet wired (S10). bucket={} key={}", bucket, objectKey);
        throw new BusinessException(ErrCode.INTERNAL_ERROR,
                "msgkey:file.error.obs_not_configured");
    }

    @Override
    public void promote(String bucket, String objectKey, String storageClass) {
        // S10: Call Aliyun OSS SetObjectStorageClass API.
        LOG.warn("ObsAttachmentStorage.promote called — OSS SDK not yet wired (S10). bucket={} key={} class={}", bucket, objectKey, storageClass);
        throw new BusinessException(ErrCode.INTERNAL_ERROR,
                "msgkey:file.error.obs_not_configured");
    }

    @Override
    public String name() {
        return "obs";
    }
}
