package com.longfeng.fileservice.provider;

import java.time.Duration;

/**
 * AttachmentStorage SPI — storage back-end abstraction (TDD §0.9 D-Storage).
 *
 * <p>Three implementations selected at runtime via {@code app.storage.provider}:
 * <ul>
 *   <li>{@code minio}  → {@link MinioAttachmentStorage} (dev profile)
 *   <li>{@code obs}    → {@link ObsAttachmentStorage} (prod-cn / Aliyun OSS)
 *   <li>{@code s3}     → {@link S3AttachmentStorage} (prod-overseas / AWS S3)
 * </ul>
 *
 * <p>C7 note: no byte[] parameters or return values for image content on this SPI.
 * Image bytes must be streamed directly to/from the object store; the service only holds metadata.
 */
public interface AttachmentStorage {

    /**
     * Generate a presigned upload URL (PUT method).
     *
     * @param bucket    target bucket name
     * @param objectKey D-OSS-Key path
     * @param mimeType  MIME type for the Content-Type hint
     * @param ttl       URL validity duration (≤ 15 min per D-OSS-TTL)
     * @return presign result containing the upload URL and expiry information
     */
    PresignResult presign(String bucket, String objectKey, String mimeType, Duration ttl);

    /**
     * Generate a presigned download URL (GET method).
     *
     * @param bucket    source bucket name
     * @param objectKey D-OSS-Key path
     * @param ttl       URL validity duration
     * @return presigned GET URL
     */
    String get(String bucket, String objectKey, Duration ttl);

    /**
     * Soft-delete (or mark for deletion) an object.
     * Actual OSS object deletion is deferred to FileTtlSweepJob (D-OSS-TTL).
     *
     * @param bucket    bucket name
     * @param objectKey D-OSS-Key path
     */
    void delete(String bucket, String objectKey);

    /**
     * Promote an object's storage class (e.g., STANDARD → IA → ARCHIVE).
     * Called by {@link com.longfeng.fileservice.job.FileTtlSweepJob}.
     *
     * @param bucket       bucket name
     * @param objectKey    D-OSS-Key path
     * @param storageClass target storage class (IA / ARCHIVE)
     */
    void promote(String bucket, String objectKey, String storageClass);

    /**
     * Name of this storage provider — used for logging and metrics tagging.
     *
     * @return provider name (minio / obs / s3)
     */
    String name();

    /**
     * Presign result DTO.
     *
     * @param uploadUrl     PUT URL for the client to upload directly
     * @param objectKey     D-OSS-Key path echoed back for reference
     * @param expiresInSec  seconds until the presigned URL expires
     */
    record PresignResult(String uploadUrl, String objectKey, long expiresInSec) {}
}
