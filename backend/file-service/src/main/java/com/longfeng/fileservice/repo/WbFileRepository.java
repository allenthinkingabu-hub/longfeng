package com.longfeng.fileservice.repo;

import com.longfeng.fileservice.entity.WbFile;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link WbFile}.
 *
 * <p>TDD §4.14 · presign callback writes here · FileTtlSweepJob reads here.
 */
public interface WbFileRepository extends JpaRepository<WbFile, Long> {

    /** Look up by D-OSS-Key path. */
    Optional<WbFile> findByObjectKey(String objectKey);
}
