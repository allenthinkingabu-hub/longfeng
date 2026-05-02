package com.longfeng.fileservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * WbFile entity · maps to file.wb_file (V1.0.080).
 *
 * <p>TDD §4.14 · D-OSS-Key · C9 (TIMESTAMPTZ → OffsetDateTime).
 * Status constants: 0=PENDING, 1=UPLOADED, 2=SCANNED_OK, 3=QUARANTINED, 9=DELETED.
 */
@Entity
@Table(name = "wb_file", schema = "file")
public class WbFile {

    public static final int STATUS_PENDING = 0;
    public static final int STATUS_UPLOADED = 1;
    public static final int STATUS_SCANNED_OK = 2;
    public static final int STATUS_QUARANTINED = 3;
    public static final int STATUS_DELETED = 9;

    @Id
    @Column(name = "id", nullable = false)
    private Long id;

    /** C2: 多租户隔离. */
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId = 0L;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    /** D-OSS-Key: wrongbook/{tenantId}/{yyyyMM}/{studentId}/{snowflakeId}_{sanitizedFilename}. */
    @Column(name = "object_key", nullable = false, length = 512, unique = true)
    private String objectKey;

    @Column(name = "original_name", length = 255)
    private String originalName;

    @Column(name = "mime_type", length = 64)
    private String mimeType;

    /** File size in bytes. Not byte[] - only metadata stored here (C7). */
    @Column(name = "bytes")
    private Long bytes;

    /** SHA-256 hex (64 chars) — content-addressable dedup. */
    @Column(name = "sha256_hash", length = 64)
    private String sha256Hash;

    /** 0=PENDING 1=UPLOADED 2=SCANNED_OK 3=QUARANTINED 9=DELETED. */
    @Column(name = "status", nullable = false)
    private Integer status = STATUS_PENDING;

    /** STANDARD / IA / ARCHIVE · D-OSS-TTL 冷热分层. */
    @Column(name = "storage_class", length = 16)
    private String storageClass = "STANDARD";

    /** C9: TIMESTAMPTZ. Filled after presign callback. */
    @Column(name = "uploaded_at", columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime uploadedAt;

    /** C9: TIMESTAMPTZ. */
    @Column(name = "created_at", nullable = false, updatable = false,
            columnDefinition = "TIMESTAMPTZ DEFAULT now()")
    private OffsetDateTime createdAt;

    // ── accessors ──────────────────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }

    public String getObjectKey() { return objectKey; }
    public void setObjectKey(String objectKey) { this.objectKey = objectKey; }

    public String getOriginalName() { return originalName; }
    public void setOriginalName(String originalName) { this.originalName = originalName; }

    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }

    public Long getBytes() { return bytes; }
    public void setBytes(Long bytes) { this.bytes = bytes; }

    public String getSha256Hash() { return sha256Hash; }
    public void setSha256Hash(String sha256Hash) { this.sha256Hash = sha256Hash; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public String getStorageClass() { return storageClass; }
    public void setStorageClass(String storageClass) { this.storageClass = storageClass; }

    public OffsetDateTime getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(OffsetDateTime uploadedAt) { this.uploadedAt = uploadedAt; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
