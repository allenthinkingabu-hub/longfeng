package com.longfeng.anonymous.share;

import com.longfeng.anonymous.entity.ShareTokenAudit;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * ShareTokenAudit Spring Data JPA repository · TDD §3.1 share/ + §4.11 V1.0.073.
 *
 * <p>Audit rows are immutable once created; only {@code upgraded_student_id} may be back-filled
 * when a viewer later registers as a student (conversion funnel).
 *
 * <p>C3 RED LINE: all queries operate only on {@code anon.share_token_audit} — never {@code wb_*}.
 */
public interface ShareTokenAuditRepository extends JpaRepository<ShareTokenAudit, Long> {

  /**
   * Find all audit entries for a given JTI, ordered by viewed_at descending.
   * Used for share analytics (view count per token).
   */
  @Query(
      value =
          "SELECT * FROM anon.share_token_audit "
              + "WHERE jti = :jti ORDER BY viewed_at DESC",
      nativeQuery = true)
  List<ShareTokenAudit> findByJtiOrdered(@Param("jti") String jti);

  /**
   * Count total views for a given JTI.
   */
  @Query(
      value = "SELECT COUNT(*) FROM anon.share_token_audit WHERE jti = :jti",
      nativeQuery = true)
  long countByJti(@Param("jti") String jti);

  /**
   * Back-fill the upgraded_student_id when a viewer completes registration.
   * Only updates rows where upgraded_student_id is NULL to prevent overwrite.
   */
  @Modifying
  @Query(
      value =
          "UPDATE anon.share_token_audit "
              + "SET upgraded_student_id = :studentId "
              + "WHERE jti = :jti AND viewer_device_fp = :deviceFp "
              + "  AND upgraded_student_id IS NULL",
      nativeQuery = true)
  int backfillUpgradedStudent(
      @Param("jti") String jti,
      @Param("deviceFp") String deviceFp,
      @Param("studentId") Long studentId);
}
