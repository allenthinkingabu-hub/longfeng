package com.longfeng.anonymous.observer;

import com.longfeng.anonymous.entity.ObserverSession;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * ObserverSession Spring Data JPA repository · TDD §3.1 observer/ + §4.12 V1.0.075.
 *
 * <p>CAS revocation: {@code casRevoke} atomically updates status to REVOKED_BY_STUDENT(3)
 * and sets {@code revoked_by_student_at} only when status is ACTIVE(1) AND version matches
 * the expected value. This prevents concurrent expire/revoke races (D-State).
 *
 * <p>C3 RED LINE: all queries operate only on {@code anon.observer_session} — never {@code wb_*}.
 */
public interface ObserverSessionRepository extends JpaRepository<ObserverSession, Long> {

  /**
   * Find a session by its JWT ID. Used by ObserverSessionService and gateway ObserverFilter.
   */
  Optional<ObserverSession> findByJti(String jti);

  /**
   * Find all ACTIVE sessions for a given student (student's "manage observers" list).
   */
  @Query(
      value =
          "SELECT * FROM anon.observer_session "
              + "WHERE student_id = :studentId AND status = 1 "
              + "ORDER BY issued_at DESC",
      nativeQuery = true)
  List<ObserverSession> findActiveByStudentId(@Param("studentId") Long studentId);

  /**
   * CAS revoke: ACTIVE(1) → REVOKED_BY_STUDENT(3) + records revocation timestamp.
   * Requires version to match (D-State · prevents concurrent expire race).
   * Returns 1 on success, 0 if CAS failed (version mismatch or already not ACTIVE).
   */
  @Modifying
  @Query(
      value =
          "UPDATE anon.observer_session "
              + "SET status = 3, "
              + "    version = version + 1, "
              + "    revoked_by_student_at = :revokedAt "
              + "WHERE jti = :jti AND status = 1 AND version = :expectedVersion",
      nativeQuery = true)
  int casRevoke(
      @Param("jti") String jti,
      @Param("expectedVersion") int expectedVersion,
      @Param("revokedAt") OffsetDateTime revokedAt);

  /**
   * Batch-expire sessions whose {@code expires_at < now()} and are still ACTIVE.
   * Used by {@code ObserverSessionGcJob}; leverages idx_obs_student index.
   */
  @Modifying
  @Query(
      value =
          "UPDATE anon.observer_session "
              + "SET status = 2, version = version + 1 "
              + "WHERE expires_at < :now AND status = 1",
      nativeQuery = true)
  int expireBefore(@Param("now") OffsetDateTime now);

  /**
   * Fetch active sessions that are past their expiry for GC logging before bulk expire.
   */
  @Query(
      value =
          "SELECT * FROM anon.observer_session "
              + "WHERE expires_at < :now AND status = 1 "
              + "ORDER BY expires_at ASC LIMIT :limit",
      nativeQuery = true)
  List<ObserverSession> findExpiringBatch(
      @Param("now") OffsetDateTime now, @Param("limit") int limit);
}
