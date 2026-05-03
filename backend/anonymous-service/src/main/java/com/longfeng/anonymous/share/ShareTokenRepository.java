package com.longfeng.anonymous.share;

import com.longfeng.anonymous.entity.ShareToken;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * ShareToken Spring Data JPA repository · TDD §3.1 share/ + §4.11 V1.0.072.
 *
 * <p>CAS revocation: the {@code casRevoke} method atomically updates status to REVOKED
 * only when the current status is ACTIVE, preventing double-revoke races.
 *
 * <p>C3 RED LINE: all queries operate only on {@code anon.share_token} — never {@code wb_*}.
 */
public interface ShareTokenRepository extends JpaRepository<ShareToken, Long> {

  /**
   * Find a share token by its JWT ID. Used by ShareTokenService.verify() and gateway validation.
   */
  Optional<ShareToken> findByJti(String jti);

  /**
   * Find all active tokens for a given sharer, ordered by most recent first.
   * Used for the sharer's "my share history" listing.
   */
  @Query(
      value =
          "SELECT * FROM anon.share_token "
              + "WHERE sharer_student_id = :studentId AND status = 1 "
              + "ORDER BY created_at DESC LIMIT :limit",
      nativeQuery = true)
  List<ShareToken> findActiveBySharer(
      @Param("studentId") Long studentId, @Param("limit") int limit);

  /**
   * Atomically increment usage_count if the token is still ACTIVE and below usage_limit.
   * Returns rows updated (1 = success, 0 = already exhausted or not ACTIVE).
   * Sets status → EXHAUSTED (4) when count reaches the limit.
   */
  @Modifying
  @Query(
      value =
          "UPDATE anon.share_token "
              + "SET usage_count = usage_count + 1, "
              + "    status = CASE WHEN usage_count + 1 >= usage_limit THEN 4 ELSE 1 END "
              + "WHERE id = :id AND status = 1 AND usage_count < usage_limit",
      nativeQuery = true)
  int incrementUsageCount(@Param("id") Long id);

  /**
   * CAS revoke: transitions ACTIVE(1) → REVOKED(3).
   * Returns 1 on success, 0 if already revoked/expired/exhausted.
   */
  @Modifying
  @Query(
      value =
          "UPDATE anon.share_token SET status = 3 "
              + "WHERE jti = :jti AND status = 1",
      nativeQuery = true)
  int casRevoke(@Param("jti") String jti);

  /**
   * Batch-expire tokens whose {@code expires_at < now()} and are still ACTIVE.
   * Used by {@code ShareTokenExpiryJob}; updates status → EXPIRED(2).
   */
  @Modifying
  @Query(
      value =
          "UPDATE anon.share_token SET status = 2 "
              + "WHERE expires_at < :now AND status = 1",
      nativeQuery = true)
  int expireBefore(@Param("now") OffsetDateTime now);

  /**
   * Find REVOKED tokens that need to be synced into the Redis Bloom filter.
   * Returns at most {@code limit} rows ordered by created_at asc (oldest first).
   */
  @Query(
      value =
          "SELECT * FROM anon.share_token "
              + "WHERE status = 3 "
              + "ORDER BY created_at ASC LIMIT :limit",
      nativeQuery = true)
  List<ShareToken> findRevokedBatch(@Param("limit") int limit);
}
