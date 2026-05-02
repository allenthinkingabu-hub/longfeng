package com.longfeng.fileservice.controller;

import com.longfeng.common.dto.ApiResult;
import com.longfeng.common.exception.BusinessException;
import com.longfeng.common.exception.ErrCode;
import com.longfeng.fileservice.entity.WbFile;
import com.longfeng.fileservice.provider.AttachmentStorage;
import com.longfeng.fileservice.provider.AttachmentStorage.PresignResult;
import com.longfeng.fileservice.repo.WbFileRepository;
import com.longfeng.fileservice.repo.WbFileLifecycleRepository;
import com.longfeng.fileservice.entity.WbFileLifecycle;
import com.longfeng.fileservice.support.ObjectKeyBuilder;
import com.longfeng.fileservice.support.SnowflakeIdGenerator;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Presign controller — TDD §12.5.1 POST /api/files/presign.
 *
 * <p>Generates a D-OSS-Key path via {@link ObjectKeyBuilder}, creates a presigned PUT URL
 * via {@link AttachmentStorage}, saves a {@link WbFile} PENDING record, and returns the URL
 * to the frontend for direct OSS upload.
 *
 * <p>C7: no byte[] in this controller or its service dependencies.
 * C8: all BusinessException messages carry "msgkey:" prefix.
 * C9: OffsetDateTime for all time fields.
 */
@RestController
@RequestMapping("/api/files")
@Validated
public class PresignController {

    private static final Logger LOG = LoggerFactory.getLogger(PresignController.class);

    /** Allowed MIME types for upload. */
    private static final Set<String> ALLOWED_MIME =
            Set.of("image/jpeg", "image/png", "image/heic", "image/webp", "application/pdf");

    private final AttachmentStorage storage;
    private final ObjectKeyBuilder keyBuilder;
    private final SnowflakeIdGenerator idGen;
    private final WbFileRepository fileRepo;
    private final WbFileLifecycleRepository lifecycleRepo;

    @Value("${app.storage.presign-ttl-min:15}")
    private long presignTtlMin;

    @Value("${app.storage.minio.bucket:wrongbook-dev}")
    private String defaultBucket;

    /** D-OSS-TTL: 30d→IA. */
    @Value("${app.storage.lifecycle.ia-after-days:30}")
    private long iaAfterDays;

    /** D-OSS-TTL: 180d→ARCHIVE. */
    @Value("${app.storage.lifecycle.archive-after-days:180}")
    private long archiveAfterDays;

    public PresignController(AttachmentStorage storage,
                             ObjectKeyBuilder keyBuilder,
                             SnowflakeIdGenerator idGen,
                             WbFileRepository fileRepo,
                             WbFileLifecycleRepository lifecycleRepo) {
        this.storage = storage;
        this.keyBuilder = keyBuilder;
        this.idGen = idGen;
        this.fileRepo = fileRepo;
        this.lifecycleRepo = lifecycleRepo;
    }

    /**
     * POST /api/files/presign
     *
     * <p>Request body (TDD §12.5.1):
     * <pre>
     * { "mimeType": "image/jpeg", "bytes": 4500000, "purpose": "wrongbook" }
     * </pre>
     *
     * <p>Response:
     * <pre>
     * { "url": "https://...", "method": "PUT", "objectKey": "wrongbook/...", "expiresInSec": 900 }
     * </pre>
     *
     * @param tenantId  injected from gateway header X-Tenant-Id (default 0 in dev)
     * @param studentId injected from gateway header X-User-Id
     */
    @PostMapping("/presign")
    public ResponseEntity<ApiResult<PresignRespBody>> presign(
            @Valid @RequestBody PresignReqBody req,
            @RequestHeader(value = "X-Tenant-Id", defaultValue = "0") long tenantId,
            @RequestHeader(value = "X-User-Id", defaultValue = "0") long studentId) {

        // MIME validation
        if (!ALLOWED_MIME.contains(req.mimeType())) {
            throw new BusinessException(ErrCode.VALIDATION_FAILED,
                    "msgkey:file.error.mime_not_allowed");
        }
        // Size validation (10 MB cap) - done via @Max annotation, but double-check here
        if (req.bytes() != null && req.bytes() > 10_485_760L) {
            throw new BusinessException(ErrCode.VALIDATION_FAILED,
                    "msgkey:file.error.file_too_large");
        }

        long snowflakeId = idGen.nextId();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        // D-OSS-Key path: wrongbook/{tenantId}/{yyyyMM}/{studentId}/{snowflakeId}_{sanitizedFilename}
        // originalName not provided in presign — use purpose as filename stub
        String originalName = req.purpose() != null ? req.purpose() + ".bin" : "upload.bin";
        String objectKey = keyBuilder.build(tenantId, studentId, snowflakeId, originalName, now);

        // Determine bucket (could vary by purpose, but for MVP use default)
        String bucket = defaultBucket;

        Duration ttl = Duration.ofMinutes(presignTtlMin);
        PresignResult pr = storage.presign(bucket, objectKey, req.mimeType(), ttl);

        // Persist PENDING metadata record (C7: no bytes stored here, only size number)
        WbFile file = new WbFile();
        file.setId(snowflakeId);
        file.setTenantId(tenantId);
        file.setStudentId(studentId);
        file.setObjectKey(objectKey);
        file.setMimeType(req.mimeType());
        file.setBytes(req.bytes());
        file.setStatus(WbFile.STATUS_PENDING);
        file.setStorageClass("STANDARD");
        file.setCreatedAt(now);
        fileRepo.save(file);

        // Persist lifecycle record (D-OSS-TTL)
        WbFileLifecycle lifecycle = new WbFileLifecycle();
        lifecycle.setFileId(snowflakeId);
        lifecycle.setFile(file);
        lifecycle.setTenantId(tenantId);
        lifecycle.setPromoteAt(now.plusDays(iaAfterDays));
        lifecycle.setArchiveAt(now.plusDays(archiveAfterDays));
        lifecycleRepo.save(lifecycle);

        LOG.info("presign bucket={} key={} provider={} ttlMin={}", bucket, objectKey, storage.name(), presignTtlMin);

        PresignRespBody body = new PresignRespBody(
                pr.uploadUrl(),
                "PUT",
                objectKey,
                pr.expiresInSec());

        return ResponseEntity.ok(ApiResult.ok(body));
    }

    // ── Inner DTOs ──────────────────────────────────────────────────────────

    /** TDD §12.5.1 request body. */
    public record PresignReqBody(
            @NotBlank String mimeType,
            @NotNull @Min(0) @Max(10_485_760) Long bytes,
            String purpose) {}

    /** TDD §12.5.1 response body. */
    public record PresignRespBody(
            String url,
            String method,
            String objectKey,
            long expiresInSec) {}
}
