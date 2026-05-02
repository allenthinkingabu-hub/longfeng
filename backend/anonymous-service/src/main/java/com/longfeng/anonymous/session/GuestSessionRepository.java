package com.longfeng.anonymous.session;

import com.longfeng.anonymous.entity.GuestSession;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * GuestSession Spring Data JPA repository · TDD §3.1 session/ + §4.10 V1.0.070.
 *
 * <p>CAS transitions use {@code @Modifying} native queries that include both
 * {@code status = :expectedStatus} AND {@code version = :expectedVersion} in the WHERE clause
 * (D-State mandate). The caller checks rows-affected == 1; 0 rows means concurrent modification.
 *
 * <p>C3 RED LINE: all queries operate only on {@code anon.guest_session} — never {@code wb_*}.
 */
public interface GuestSessionRepository extends JpaRepository<GuestSession, Long> {

  /**
   * CAS status transition. Returns the number of rows updated (1 = success, 0 = CAS conflict).
   * D-State: prevents concurrent expire/claim race.
   */
  @Modifying
  @Query(
      value =
          "UPDATE anon.guest_session "
              + "SET status = :newStatus, version = version + 1 "
              + "WHERE id = :id AND status = :expectedStatus AND version = :expectedVersion",
      nativeQuery = true)
  int casTransition(
      @Param("id") Long id,
      @Param("expectedStatus") short expectedStatus,
      @Param("newStatus") short newStatus,
      @Param("expectedVersion") int expectedVersion);

  /**
   * CAS transition: ANALYZING → RESULT_READY with JSON payload.
   * Atomically updates analysis_result_json AND status in one SQL statement to avoid
   * a spurious version increment from a prior save().
   */
  @Modifying
  @Query(
      value =
          "UPDATE anon.guest_session "
              + "SET status = 2, version = version + 1, "
              + "    analysis_result_json = CAST(:json AS jsonb) "
              + "WHERE id = :id AND status = 1 AND version = :expectedVersion",
      nativeQuery = true)
  int casMarkResultReady(
      @Param("id") Long id,
      @Param("json") String analysisResultJson,
      @Param("expectedVersion") int expectedVersion);

  /**
   * CAS claim transition — atomically moves session to CLAIMED status and records claim metadata.
   */
  @Modifying
  @Query(
      value =
          "UPDATE anon.guest_session "
              + "SET status = 4, version = version + 1, "
              + "    claimed_by_student_id = :studentId, "
              + "    claimed_question_id = :questionId, "
              + "    claimed_at = now() "
              + "WHERE id = :id AND status IN (1, 2) AND version = :expectedVersion",
      nativeQuery = true)
  int casClaim(
      @Param("id") Long id,
      @Param("studentId") Long studentId,
      @Param("questionId") Long questionId,
      @Param("expectedVersion") int expectedVersion);

  /**
   * Batch-expire sessions whose {@code expires_at < now()} and are still in an active state.
   * Used by {@code GuestSessionExpiryJob}; leverages the BRIN index on {@code expires_at}.
   */
  @Modifying
  @Query(
      value =
          "UPDATE anon.guest_session "
              + "SET status = 9, version = version + 1 "
              + "WHERE expires_at < :now AND status NOT IN (4, 9)",
      nativeQuery = true)
  int expireBefore(@Param("now") OffsetDateTime now);

  /**
   * Fetch a batch of sessions expiring before the given timestamp, for logging before expiry.
   * Limit prevents memory overload in a single sweep.
   */
  @Query(
      value =
          "SELECT * FROM anon.guest_session "
              + "WHERE expires_at < :now AND status NOT IN (4, 9) "
              + "ORDER BY expires_at ASC LIMIT :limit",
      nativeQuery = true)
  List<GuestSession> findExpiringBatch(
      @Param("now") OffsetDateTime now, @Param("limit") int limit);
}
