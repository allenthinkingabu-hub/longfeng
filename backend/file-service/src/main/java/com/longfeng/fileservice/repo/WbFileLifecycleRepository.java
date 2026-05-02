package com.longfeng.fileservice.repo;

import com.longfeng.fileservice.entity.WbFileLifecycle;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data JPA repository for {@link WbFileLifecycle}.
 *
 * <p>TDD §4.14 · D-OSS-TTL: FileTtlSweepJob uses these queries to drive lifecycle transitions.
 */
public interface WbFileLifecycleRepository extends JpaRepository<WbFileLifecycle, Long> {

    /**
     * Files due for IA promotion: promote_at is in the past and not null.
     * C9: OffsetDateTime parameter.
     */
    @Query("SELECT lc FROM WbFileLifecycle lc WHERE lc.promoteAt IS NOT NULL AND lc.promoteAt <= :now")
    List<WbFileLifecycle> findDueForIaPromotion(@Param("now") OffsetDateTime now);

    /**
     * Files due for ARCHIVE transition: archive_at is in the past and not null.
     * C9: OffsetDateTime parameter.
     */
    @Query("SELECT lc FROM WbFileLifecycle lc WHERE lc.archiveAt IS NOT NULL AND lc.archiveAt <= :now")
    List<WbFileLifecycle> findDueForArchive(@Param("now") OffsetDateTime now);

    /**
     * Files due for hard delete: delete_at is in the past and not null.
     * C9: OffsetDateTime parameter.
     */
    @Query("SELECT lc FROM WbFileLifecycle lc WHERE lc.deleteAt IS NOT NULL AND lc.deleteAt <= :now")
    List<WbFileLifecycle> findDueForDelete(@Param("now") OffsetDateTime now);

    /**
     * Batch-clear promote_at after IA transition is complete (null = no longer pending).
     */
    @Modifying
    @Query("UPDATE WbFileLifecycle lc SET lc.promoteAt = null WHERE lc.fileId IN :fileIds")
    int clearPromoteAt(@Param("fileIds") List<Long> fileIds);
}
