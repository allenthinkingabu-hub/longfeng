package com.longfeng.anonymous.observer;

import com.longfeng.anonymous.entity.ObserverSession;
import com.longfeng.common.exception.BusinessException;
import com.longfeng.common.exception.ErrCode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ObserverSessionService — observer session listing and student-initiated revocation.
 *
 * <p>TDD §3.1 observer/ + D-Observer-Revoke:
 * <ul>
 *   <li>Lists active observer sessions for a given student.
 *   <li>Revokes a session via CAS UPDATE on {@code version} (D-State / C2).
 *   <li>Writes {@code obs:revoked:{jti}} to Redis SETEX for gateway real-time pickup (≤ 1 s).
 * </ul>
 *
 * <p>C4 RED LINE: this service never modifies the {@code scope} field of any session.
 * C3 RED LINE: never reads/writes {@code wb_*} tables.
 * C8: all {@link BusinessException} carry {@code msgkey:} prefix.
 */
@Service
@Transactional
public class ObserverSessionService {

  private static final Logger log = LoggerFactory.getLogger(ObserverSessionService.class);

  /** Redis key prefix for observer token revocations (D-Observer-Revoke). */
  static final String OBS_REVOKE_KEY_PREFIX = "obs:revoked:";

  private final ObserverSessionRepository sessionRepo;
  private final StringRedisTemplate redis;

  public ObserverSessionService(ObserverSessionRepository sessionRepo, StringRedisTemplate redis) {
    this.sessionRepo = sessionRepo;
    this.redis = redis;
  }

  // ── List ─────────────────────────────────────────────────────────────────

  /**
   * Returns all ACTIVE observer sessions for a student.
   * Used in P13 Settings → "Manage Observers" view.
   *
   * @param studentId the owning student
   * @return list of ACTIVE {@link ObserverSession} records
   */
  @Transactional(readOnly = true)
  public List<ObserverSession> listActive(Long studentId) {
    return sessionRepo.findActiveByStudentId(studentId);
  }

  // ── Revoke ───────────────────────────────────────────────────────────────

  /**
   * Student-initiated revocation of an observer session.
   *
   * <p>Steps:
   * <ol>
   *   <li>Load session by JTI; verify that the requesting student owns it.
   *   <li>CAS UPDATE: ACTIVE(1) → REVOKED_BY_STUDENT(3) + set {@code revoked_by_student_at}.
   *       Retries once if CAS fails due to concurrent expire race.
   *   <li>Write {@code obs:revoked:{jti}} Redis SETEX with remaining TTL (D-Observer-Revoke).
   * </ol>
   *
   * <p>Idempotent: if session is already REVOKED_BY_STUDENT, logs and returns without error.
   *
   * @param jti              the observer JWT ID to revoke
   * @param requestingStudentId the student requesting revocation (must match session.student_id)
   * @throws BusinessException NOT_FOUND if JTI not found
   * @throws BusinessException ANONYMOUS_WRITE_FORBIDDEN if not the owning student
   * @throws BusinessException INTERNAL_ERROR if CAS fails after retry
   */
  public void revokeByStudent(String jti, Long requestingStudentId) {
    ObserverSession session = sessionRepo.findByJti(jti)
        .orElseThrow(() -> new BusinessException(ErrCode.NOT_FOUND,
            "msgkey:anon.observer.session_not_found"));

    // Ownership check
    if (!session.getStudentId().equals(requestingStudentId)) {
      throw new BusinessException(ErrCode.ANONYMOUS_WRITE_FORBIDDEN,
          "msgkey:anon.observer.not_owner");
    }

    // Idempotent: already revoked
    if (session.getStatus() == ObserverSession.STATUS_REVOKED_BY_STUDENT) {
      log.debug("observer-session jti={} already revoked by student — idempotent no-op", jti);
      writeRevocationToRedis(jti, session.getExpiresAt());
      return;
    }

    // Already expired — treat as benign no-op
    if (session.getStatus() == ObserverSession.STATUS_EXPIRED) {
      log.debug("observer-session jti={} already expired, revoke no-op", jti);
      return;
    }

    // CAS revoke
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    int rows = sessionRepo.casRevoke(jti, session.getVersion(), now);

    if (rows == 0) {
      // Re-read once to handle concurrent expire
      ObserverSession refreshed = sessionRepo.findByJti(jti).orElseThrow();
      if (refreshed.getStatus() == ObserverSession.STATUS_REVOKED_BY_STUDENT) {
        log.debug("observer-session jti={} revoked concurrently — idempotent", jti);
        writeRevocationToRedis(jti, refreshed.getExpiresAt());
        return;
      }
      if (refreshed.getStatus() == ObserverSession.STATUS_EXPIRED) {
        log.debug("observer-session jti={} expired concurrently during revoke", jti);
        return;
      }
      throw new BusinessException(ErrCode.INTERNAL_ERROR,
          "msgkey:anon.observer.cas_conflict");
    }

    log.info("observer-session jti={} revoked by student={}", jti, requestingStudentId);
    writeRevocationToRedis(jti, session.getExpiresAt());
  }

  // ── Internal helpers ─────────────────────────────────────────────────────

  /**
   * Writes {@code obs:revoked:{jti}} to Redis with TTL equal to the remaining session lifetime.
   * Non-fatal: a Redis failure is logged but does not roll back the DB revocation.
   * The gateway will fallback to DB-level check or the GC job will eventually sync.
   */
  private void writeRevocationToRedis(String jti, OffsetDateTime expiresAt) {
    Duration remaining = Duration.between(OffsetDateTime.now(ZoneOffset.UTC), expiresAt);
    if (remaining.isNegative() || remaining.isZero()) {
      // Token already past expiry, no need to write Redis
      return;
    }
    try {
      redis.opsForValue().set(OBS_REVOKE_KEY_PREFIX + jti, "1",
          remaining.getSeconds(), TimeUnit.SECONDS);
      log.debug("observer-session jti={} written to Redis revocation set, ttl={}s",
          jti, remaining.getSeconds());
    } catch (Exception ex) {
      log.warn("Redis write failed for observer revocation jti={}: {}", jti, ex.getMessage());
      // Non-fatal — D-Observer-Revoke: gateway has 1s window; fallback is GC job
    }
  }
}
