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
import com.fasterxml.jackson.annotation.JsonProperty;
import com.longfeng.fileservice.support.ObjectKeyBuilder;
import com.longfeng.fileservice.support.SnowflakeIdGenerator;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Presign controller — TDD §12.5.1 POST /api/file/presign.
 *
 * <p>Generates a D-OSS-Key path via {@link ObjectKeyBuilder}, creates a presigned PUT URL
 * via {@link AttachmentStorage}, saves a {@link WbFile} PENDING record, and returns the URL
 * to the frontend for direct OSS upload.
 *
 * <p>Path is intentionally singular ({@code /api/file}) to align with the frontend
 * GuestCapture call site (frontend/apps/h5/src/pages/GuestCapture/index.tsx). The
 * request DTO uses snake_case ({@code content_type}) to match the FE JSON payload.
 *
 * <p>C7: no byte[] in this controller or its service dependencies.
 * C8: all BusinessException messages carry "msgkey:" prefix.
 * C9: OffsetDateTime for all time fields.
 */
@RestController
@RequestMapping("/api/file")
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
     * POST /api/file/presign
     *
     * <p>Request body (snake_case to match frontend GuestCapture payload):
     * <pre>
     * { "filename": "math.jpg", "content_type": "image/jpeg" }
     * </pre>
     *
     * <p>Optional fields {@code bytes} and {@code purpose} are accepted for forward
     * compatibility but are not currently sent by the frontend.
     *
     * <p>Response (snake_case for FE; only {@code url} + {@code image_url} are consumed today):
     * <pre>
     * {
     *   "url": "https://minio/.../put?sig=...",
     *   "image_url": "https://minio/.../get?sig=...",
     *   "method": "PUT",
     *   "object_key": "wrongbook/...",
     *   "expires_in_sec": 900
     * }
     * </pre>
     *
     * @param tenantId  injected from gateway header X-Tenant-Id (default 0 in dev)
     * @param studentId injected from gateway header X-User-Id
     */
    @PostMapping("/presign")
    @Transactional
    public ResponseEntity<ApiResult<PresignRespBody>> presign(
            @Valid @RequestBody PresignReqBody req,
            @RequestHeader(value = "X-Tenant-Id", defaultValue = "0") long tenantId,
            @RequestHeader(value = "X-User-Id", defaultValue = "0") long studentId) {

        // MIME validation
        if (!ALLOWED_MIME.contains(req.contentType())) {
            throw new BusinessException(ErrCode.VALIDATION_FAILED,
                    "msgkey:file.error.mime_not_allowed");
        }
        // Size validation (10 MB cap) - @Max annotation also enforces, but double-check
        // when bytes is provided. FE does not send bytes today, so null is allowed.
        if (req.bytes() != null && req.bytes() > 10_485_760L) {
            throw new BusinessException(ErrCode.VALIDATION_FAILED,
                    "msgkey:file.error.file_too_large");
        }

        long snowflakeId = idGen.nextId();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        // D-OSS-Key path: wrongbook/{tenantId}/{yyyyMM}/{studentId}/{snowflakeId}_{sanitizedFilename}
        // Use the FE-supplied filename so the object key preserves the real extension.
        String originalName = req.filename();
        String objectKey = keyBuilder.build(tenantId, studentId, snowflakeId, originalName, now);

        // Determine bucket (could vary by purpose, but for MVP use default)
        String bucket = defaultBucket;

        Duration ttl = Duration.ofMinutes(presignTtlMin);
        PresignResult pr = storage.presign(bucket, objectKey, req.contentType(), ttl);

        // Long-lived GET URL for the FE to hand to downstream OCR / display.
        // 24h is well within typical OCR + first-render windows.
        String imageUrl = storage.get(bucket, objectKey, Duration.ofHours(24));

        // Persist PENDING metadata record (C7: no bytes stored here, only size number).
        // saveAndFlush ensures the wb_file row is INSERT-ed before the lifecycle row
        // references it via @MapsId. Without flush, Hibernate observed an unsaved
        // associated entity while resolving the OneToOneType during merge and threw
        // AssertionFailure: null identifier (com.longfeng.fileservice.entity.WbFileLifecycle).
        WbFile file = new WbFile();
        file.setId(snowflakeId);
        file.setTenantId(tenantId);
        file.setStudentId(studentId);
        file.setObjectKey(objectKey);
        file.setMimeType(req.contentType());
        file.setBytes(req.bytes());
        file.setStatus(WbFile.STATUS_PENDING);
        file.setStorageClass("STANDARD");
        file.setCreatedAt(now);
        file = fileRepo.saveAndFlush(file);

        // Persist lifecycle record (D-OSS-TTL).
        // Do NOT setFileId — @MapsId derives the PK from file.id. Setting both was the
        // double-set conflict that caused the original null-identifier failure.
        WbFileLifecycle lifecycle = new WbFileLifecycle();
        lifecycle.setFile(file);
        lifecycle.setTenantId(tenantId);
        lifecycle.setPromoteAt(now.plusDays(iaAfterDays));
        lifecycle.setArchiveAt(now.plusDays(archiveAfterDays));
        lifecycleRepo.save(lifecycle);

        LOG.info("presign bucket={} key={} provider={} ttlMin={}", bucket, objectKey, storage.name(), presignTtlMin);

        PresignRespBody body = new PresignRespBody(
                pr.uploadUrl(),
                imageUrl,
                "PUT",
                objectKey,
                pr.expiresInSec());

        return ResponseEntity.ok(ApiResult.ok(body));
    }

    // ── Inner DTOs ──────────────────────────────────────────────────────────

    /**
     * Request body. Uses {@link JsonProperty} to map FE snake_case ({@code content_type})
     * to Java camelCase ({@code contentType}) without forcing the rest of the codebase
     * onto a snake_case naming strategy.
     *
     * <p>{@code filename} + {@code contentType} are required (FE always sends both).
     * {@code bytes} + {@code purpose} are optional forward-compat slots.
     */
    public record PresignReqBody(
            @NotBlank String filename,
            @JsonProperty("content_type") @NotBlank String contentType,
            @JsonProperty("bytes") @Min(0) @Max(10_485_760) Long bytes,
            String purpose) {}

    /**
     * Response body. {@link JsonProperty} keeps the wire format snake_case for FE
     * while record components stay camelCase. FE consumes only {@code url} +
     * {@code image_url} today; the other fields are kept for callback / debug use.
     */
    public record PresignRespBody(
            String url,
            @JsonProperty("image_url") String imageUrl,
            String method,
            @JsonProperty("object_key") String objectKey,
            @JsonProperty("expires_in_sec") long expiresInSec) {}
}
