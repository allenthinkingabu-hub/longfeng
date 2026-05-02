package com.longfeng.fileservice.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.longfeng.common.exception.BusinessException;
import com.longfeng.common.exception.ErrCode;
import com.longfeng.fileservice.entity.WbFile;
import com.longfeng.fileservice.entity.WbFileLifecycle;
import com.longfeng.fileservice.provider.AttachmentStorage;
import com.longfeng.fileservice.repo.WbFileLifecycleRepository;
import com.longfeng.fileservice.repo.WbFileRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Unit tests for {@link FileTtlSweepJob}.
 *
 * <p>Covers:
 * <ul>
 *   <li>sweepIaPromotion: files due for IA promotion are promoted and promote_at cleared
 *   <li>sweepArchive: files due for archive are archived and archive_at cleared
 *   <li>sweepDelete: files due for delete are removed from OSS and marked DELETED
 *   <li>Error resilience: OSS failure on delete still marks file DELETED in DB
 *   <li>No files due: zero operations
 * </ul>
 *
 * <p>C7: no byte[] in this test.
 * C8: BusinessException uses msgkey: prefix.
 * C9: OffsetDateTime in all time comparisons.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FileTtlSweepJobTest {

    @Mock WbFileLifecycleRepository lifecycleRepo;
    @Mock WbFileRepository fileRepo;
    @Mock AttachmentStorage storage;

    private FileTtlSweepJob job;

    private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 5, 1, 2, 0, 0, 0, ZoneOffset.UTC);
    private static final String BUCKET = "wrongbook-dev";

    @BeforeEach
    void setUp() {
        job = new FileTtlSweepJob(lifecycleRepo, fileRepo, storage);
        setField(job, "defaultBucket", BUCKET);

        when(fileRepo.save(any(WbFile.class))).thenAnswer(inv -> inv.getArgument(0));
        when(lifecycleRepo.save(any(WbFileLifecycle.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ── IA promotion ──────────────────────────────────────────────────────

    @Test
    @DisplayName("sweepIaPromotion: promotes file to IA and clears promote_at")
    void sweepIaPromotion_promotesFileAndClearsPromoteAt() {
        WbFile file = makeFile(1L, "wrongbook/0/202601/100/1_photo.jpg", "STANDARD");
        WbFileLifecycle lc = makeLifecycle(1L, file, NOW.minusDays(1), null, null);
        when(lifecycleRepo.findDueForIaPromotion(NOW)).thenReturn(List.of(lc));

        int count = job.sweepIaPromotion(NOW);

        assertThat(count).isEqualTo(1);
        verify(storage).promote(eq(BUCKET), eq(file.getObjectKey()), eq("IA"));

        ArgumentCaptor<WbFile> fileCaptor = ArgumentCaptor.forClass(WbFile.class);
        verify(fileRepo).save(fileCaptor.capture());
        assertThat(fileCaptor.getValue().getStorageClass()).isEqualTo("IA");

        ArgumentCaptor<WbFileLifecycle> lcCaptor = ArgumentCaptor.forClass(WbFileLifecycle.class);
        verify(lifecycleRepo).save(lcCaptor.capture());
        assertThat(lcCaptor.getValue().getPromoteAt()).isNull();
    }

    @Test
    @DisplayName("sweepIaPromotion: no files due → zero promotions")
    void sweepIaPromotion_noDue_zeroCount() {
        when(lifecycleRepo.findDueForIaPromotion(NOW)).thenReturn(List.of());

        int count = job.sweepIaPromotion(NOW);

        assertThat(count).isEqualTo(0);
        verify(storage, never()).promote(any(), any(), any());
    }

    @Test
    @DisplayName("sweepIaPromotion: skips lifecycle record with null file")
    void sweepIaPromotion_nullFile_skipped() {
        WbFileLifecycle lc = new WbFileLifecycle();
        lc.setFileId(999L);
        lc.setFile(null); // no associated file
        when(lifecycleRepo.findDueForIaPromotion(NOW)).thenReturn(List.of(lc));

        int count = job.sweepIaPromotion(NOW);

        assertThat(count).isEqualTo(0);
        verify(storage, never()).promote(any(), any(), any());
    }

    // ── ARCHIVE sweep ────────────────────────────────────────────────────

    @Test
    @DisplayName("sweepArchive: promotes file to ARCHIVE and clears archive_at")
    void sweepArchive_archivesFileAndClearsArchiveAt() {
        WbFile file = makeFile(2L, "wrongbook/0/202601/100/2_photo.jpg", "IA");
        WbFileLifecycle lc = makeLifecycle(2L, file, null, NOW.minusDays(1), null);
        when(lifecycleRepo.findDueForArchive(NOW)).thenReturn(List.of(lc));

        int count = job.sweepArchive(NOW);

        assertThat(count).isEqualTo(1);
        verify(storage).promote(eq(BUCKET), eq(file.getObjectKey()), eq("ARCHIVE"));

        ArgumentCaptor<WbFile> fileCaptor = ArgumentCaptor.forClass(WbFile.class);
        verify(fileRepo).save(fileCaptor.capture());
        assertThat(fileCaptor.getValue().getStorageClass()).isEqualTo("ARCHIVE");

        ArgumentCaptor<WbFileLifecycle> lcCaptor = ArgumentCaptor.forClass(WbFileLifecycle.class);
        verify(lifecycleRepo).save(lcCaptor.capture());
        assertThat(lcCaptor.getValue().getArchiveAt()).isNull();
    }

    @Test
    @DisplayName("sweepArchive: no files due → zero archives")
    void sweepArchive_noDue_zeroCount() {
        when(lifecycleRepo.findDueForArchive(NOW)).thenReturn(List.of());

        int count = job.sweepArchive(NOW);

        assertThat(count).isEqualTo(0);
    }

    // ── Delete sweep ─────────────────────────────────────────────────────

    @Test
    @DisplayName("sweepDelete: deletes from OSS and marks file STATUS_DELETED")
    void sweepDelete_deletesFromOssAndMarksDeleted() {
        WbFile file = makeFile(3L, "wrongbook/0/202601/100/3_photo.jpg", "IA");
        WbFileLifecycle lc = makeLifecycle(3L, file, null, null, NOW.minusDays(1));
        when(lifecycleRepo.findDueForDelete(NOW)).thenReturn(List.of(lc));

        int count = job.sweepDelete(NOW);

        assertThat(count).isEqualTo(1);
        verify(storage).delete(eq(BUCKET), eq(file.getObjectKey()));

        ArgumentCaptor<WbFile> fileCaptor = ArgumentCaptor.forClass(WbFile.class);
        verify(fileRepo).save(fileCaptor.capture());
        assertThat(fileCaptor.getValue().getStatus()).isEqualTo(WbFile.STATUS_DELETED);
    }

    @Test
    @DisplayName("sweepDelete: OSS delete failure still marks file DELETED in DB (resilience)")
    void sweepDelete_ossFailure_stillMarksDeletedInDb() {
        WbFile file = makeFile(4L, "wrongbook/0/202601/100/4_photo.jpg", "ARCHIVE");
        WbFileLifecycle lc = makeLifecycle(4L, file, null, null, NOW.minusDays(1));
        when(lifecycleRepo.findDueForDelete(NOW)).thenReturn(List.of(lc));
        doThrow(new BusinessException(ErrCode.INTERNAL_ERROR, "msgkey:file.error.delete_failed"))
                .when(storage).delete(any(), any());

        int count = job.sweepDelete(NOW);

        assertThat(count).isEqualTo(1);
        // Even though OSS delete failed, DB record should be marked DELETED
        ArgumentCaptor<WbFile> fileCaptor = ArgumentCaptor.forClass(WbFile.class);
        verify(fileRepo).save(fileCaptor.capture());
        assertThat(fileCaptor.getValue().getStatus()).isEqualTo(WbFile.STATUS_DELETED);
    }

    @Test
    @DisplayName("sweepDelete: no files due → zero deletions")
    void sweepDelete_noDue_zeroCount() {
        when(lifecycleRepo.findDueForDelete(NOW)).thenReturn(List.of());

        int count = job.sweepDelete(NOW);

        assertThat(count).isEqualTo(0);
        verify(storage, never()).delete(any(), any());
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private WbFile makeFile(long id, String objectKey, String storageClass) {
        WbFile f = new WbFile();
        f.setId(id);
        f.setStudentId(100L);
        f.setTenantId(0L);
        f.setObjectKey(objectKey);
        f.setStatus(WbFile.STATUS_UPLOADED);
        f.setStorageClass(storageClass);
        return f;
    }

    private WbFileLifecycle makeLifecycle(long fileId, WbFile file,
                                          OffsetDateTime promoteAt,
                                          OffsetDateTime archiveAt,
                                          OffsetDateTime deleteAt) {
        WbFileLifecycle lc = new WbFileLifecycle();
        lc.setFileId(fileId);
        lc.setFile(file);
        lc.setTenantId(file.getTenantId());
        lc.setPromoteAt(promoteAt);
        lc.setArchiveAt(archiveAt);
        lc.setDeleteAt(deleteAt);
        return lc;
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field " + fieldName, e);
        }
    }
}
