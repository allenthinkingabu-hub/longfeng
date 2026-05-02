package com.longfeng.fileservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * WbFileLifecycle entity · maps to file.wb_file_lifecycle (V1.0.081).
 *
 * <p>1:1 relationship with {@link WbFile}.
 * TDD §4.14 · D-OSS-TTL: 30d→IA / 180d→ARCHIVE.
 * C9: all timestamp fields are OffsetDateTime (TIMESTAMPTZ).
 */
@Entity
@Table(name = "wb_file_lifecycle", schema = "file")
public class WbFileLifecycle {

    /** Shares PK with wb_file.id (1:1). */
    @Id
    @Column(name = "file_id")
    private Long fileId;

    @OneToOne
    @MapsId
    @JoinColumn(name = "file_id")
    private WbFile file;

    /** C2: 多租户隔离 · sweep job 按 tenant 分桶. */
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    /**
     * C9: TIMESTAMPTZ.
     * 30d 后转 IA · FileTtlSweepJob promote 路径.
     */
    @Column(name = "promote_at", columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime promoteAt;

    /**
     * C9: TIMESTAMPTZ.
     * 180d 后转 ARCHIVE.
     */
    @Column(name = "archive_at", columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime archiveAt;

    /**
     * C9: TIMESTAMPTZ.
     * NULL = 不自动删除 · 学生注销时填入 now() + 7d 宽限.
     */
    @Column(name = "delete_at", columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime deleteAt;

    // ── accessors ──────────────────────────────────────────────────────────

    public Long getFileId() { return fileId; }
    public void setFileId(Long fileId) { this.fileId = fileId; }

    public WbFile getFile() { return file; }
    public void setFile(WbFile file) { this.file = file; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public OffsetDateTime getPromoteAt() { return promoteAt; }
    public void setPromoteAt(OffsetDateTime promoteAt) { this.promoteAt = promoteAt; }

    public OffsetDateTime getArchiveAt() { return archiveAt; }
    public void setArchiveAt(OffsetDateTime archiveAt) { this.archiveAt = archiveAt; }

    public OffsetDateTime getDeleteAt() { return deleteAt; }
    public void setDeleteAt(OffsetDateTime deleteAt) { this.deleteAt = deleteAt; }
}
