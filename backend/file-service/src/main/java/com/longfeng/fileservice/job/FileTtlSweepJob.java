package com.longfeng.fileservice.job;

import com.longfeng.fileservice.entity.WbFile;
import com.longfeng.fileservice.entity.WbFileLifecycle;
import com.longfeng.fileservice.provider.AttachmentStorage;
import com.longfeng.fileservice.repo.WbFileLifecycleRepository;
import com.longfeng.fileservice.repo.WbFileRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * FileTtlSweepJob — D-OSS-TTL lifecycle management.
 *
 * <p>TDD §0.9 D-OSS-TTL · plan §5.S2 BE-04 · cron {@code 0 0 2 * * ?} (daily 02:00).
 *
 * <p>Three sweep passes:
 * <ol>
 *   <li><strong>promote</strong>: files where {@code promote_at <= now} → STANDARD → IA.
 *   <li><strong>archive</strong>: files where {@code archive_at <= now} → IA → ARCHIVE.
 *   <li><strong>delete</strong>: files where {@code delete_at <= now} → hard-delete from OSS + DB.
 * </ol>
 *
 * <p>C7: no byte[] held in this job — only metadata operations and OSS API calls via SPI.
 * C8: Business errors use BusinessException with msgkey: prefix (thrown by storage SPI).
 * C9: OffsetDateTime.now(UTC) for all timestamp comparisons.
 *
 * <p>Note: in production this job should be guarded by ShedLock to ensure exactly-once
 * execution across multiple pods (D-Pod decision). The {@code @Scheduled} here provides
 * a baseline local schedule; XXL-Job integration can wrap it in a handler method.
 */
@Component
public class FileTtlSweepJob {

    private static final Logger LOG = LoggerFactory.getLogger(FileTtlSweepJob.class);

    private final WbFileLifecycleRepository lifecycleRepo;
    private final WbFileRepository fileRepo;
    private final AttachmentStorage storage;

    @Value("${app.storage.minio.bucket:wrongbook-dev}")
    private String defaultBucket;

    public FileTtlSweepJob(WbFileLifecycleRepository lifecycleRepo,
                           WbFileRepository fileRepo,
                           AttachmentStorage storage) {
        this.lifecycleRepo = lifecycleRepo;
        this.fileRepo = fileRepo;
        this.storage = storage;
    }

    /**
     * Main sweep entry point.
     *
     * <p>Runs daily at 02:00 server time. In production, this is triggered by XXL-Job
     * with the handler name {@code file-lifecycle-promote} (TDD §16.7).
     * Cron: {@code 0 0 2 * * ?} (Spring cron format: sec min hr dom mon dow).
     */
    @Scheduled(cron = "${app.storage.lifecycle.sweep-cron:0 0 2 * * ?}")
    @Transactional
    public void sweep() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        LOG.info("FileTtlSweepJob.sweep starting at={}", now);

        int promoted = sweepIaPromotion(now);
        int archived = sweepArchive(now);
        int deleted = sweepDelete(now);

        LOG.info("FileTtlSweepJob.sweep done: promoted={} archived={} deleted={}", promoted, archived, deleted);
    }

    /**
     * Pass 1: Promote STANDARD → IA for files where promote_at <= now.
     * D-OSS-TTL: 30 days after upload.
     */
    int sweepIaPromotion(OffsetDateTime now) {
        List<WbFileLifecycle> due = lifecycleRepo.findDueForIaPromotion(now);
        if (due.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (WbFileLifecycle lc : due) {
            try {
                WbFile file = lc.getFile();
                if (file == null) {
                    continue;
                }
                storage.promote(defaultBucket, file.getObjectKey(), "IA");
                file.setStorageClass("IA");
                fileRepo.save(file);
                // Clear promote_at to avoid re-processing
                lc.setPromoteAt(null);
                lifecycleRepo.save(lc);
                count++;
            } catch (Exception e) {
                LOG.error("FileTtlSweepJob.sweepIaPromotion failed for fileId={}", lc.getFileId(), e);
            }
        }
        LOG.info("FileTtlSweepJob.sweepIaPromotion: promoted {} files", count);
        return count;
    }

    /**
     * Pass 2: Promote IA → ARCHIVE for files where archive_at <= now.
     * D-OSS-TTL: 180 days after upload.
     */
    int sweepArchive(OffsetDateTime now) {
        List<WbFileLifecycle> due = lifecycleRepo.findDueForArchive(now);
        if (due.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (WbFileLifecycle lc : due) {
            try {
                WbFile file = lc.getFile();
                if (file == null) {
                    continue;
                }
                storage.promote(defaultBucket, file.getObjectKey(), "ARCHIVE");
                file.setStorageClass("ARCHIVE");
                fileRepo.save(file);
                // Clear archive_at to avoid re-processing
                lc.setArchiveAt(null);
                lifecycleRepo.save(lc);
                count++;
            } catch (Exception e) {
                LOG.error("FileTtlSweepJob.sweepArchive failed for fileId={}", lc.getFileId(), e);
            }
        }
        LOG.info("FileTtlSweepJob.sweepArchive: archived {} files", count);
        return count;
    }

    /**
     * Pass 3: Hard-delete files where delete_at <= now.
     * Removes OSS object and marks wb_file status=DELETED.
     */
    int sweepDelete(OffsetDateTime now) {
        List<WbFileLifecycle> due = lifecycleRepo.findDueForDelete(now);
        if (due.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (WbFileLifecycle lc : due) {
            try {
                WbFile file = lc.getFile();
                if (file == null) {
                    continue;
                }
                // Delete from OSS (idempotent if already gone)
                try {
                    storage.delete(defaultBucket, file.getObjectKey());
                } catch (Exception ossEx) {
                    LOG.warn("FileTtlSweepJob.sweepDelete: OSS delete failed for key={} — marking deleted anyway",
                            file.getObjectKey(), ossEx);
                }
                // Mark as deleted in metadata (soft delete)
                file.setStatus(WbFile.STATUS_DELETED);
                fileRepo.save(file);
                count++;
            } catch (Exception e) {
                LOG.error("FileTtlSweepJob.sweepDelete failed for fileId={}", lc.getFileId(), e);
            }
        }
        LOG.info("FileTtlSweepJob.sweepDelete: deleted {} files", count);
        return count;
    }
}
