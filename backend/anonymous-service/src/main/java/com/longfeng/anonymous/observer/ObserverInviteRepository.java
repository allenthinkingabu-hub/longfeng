package com.longfeng.anonymous.observer;

import com.longfeng.anonymous.entity.ObserverInvite;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * ObserverInvite Spring Data JPA repository · TDD §3.1 observer/ + §4.12 V1.0.074.
 *
 * <p>C3 RED LINE: all queries operate only on {@code anon.observer_invite} — never {@code wb_*}.
 */
public interface ObserverInviteRepository extends JpaRepository<ObserverInvite, Long> {

  /**
   * Find a PENDING invite by its 6-character code.
   * Returns empty if the code has already been exchanged, expired, or revoked.
   */
  @Query(
      value =
          "SELECT * FROM anon.observer_invite "
              + "WHERE invite_code = :code AND status = 1",
      nativeQuery = true)
  Optional<ObserverInvite> findPendingByCode(@Param("code") String code);

  /**
   * Find invite by code regardless of status (for history / revocation checks).
   */
  Optional<ObserverInvite> findByInviteCode(String inviteCode);

  /**
   * Find all invites issued by a student, ordered by created_at desc.
   */
  @Query(
      value =
          "SELECT * FROM anon.observer_invite "
              + "WHERE student_id = :studentId ORDER BY created_at DESC LIMIT :limit",
      nativeQuery = true)
  List<ObserverInvite> findByStudentIdOrdered(
      @Param("studentId") Long studentId, @Param("limit") int limit);

  /**
   * Atomically mark invite as EXCHANGED (PENDING → EXCHANGED).
   * Returns 1 on success, 0 if already consumed or not PENDING.
   */
  @Modifying
  @Query(
      value =
          "UPDATE anon.observer_invite SET status = 2 "
              + "WHERE id = :id AND status = 1",
      nativeQuery = true)
  int casMarkExchanged(@Param("id") Long id);

  /**
   * Batch-expire invites whose {@code expires_at < now()} and are still PENDING.
   */
  @Modifying
  @Query(
      value =
          "UPDATE anon.observer_invite SET status = 3 "
              + "WHERE expires_at < :now AND status = 1",
      nativeQuery = true)
  int expireBefore(@Param("now") OffsetDateTime now);
}
