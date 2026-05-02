package com.longfeng.anonymous.session;

import com.longfeng.anonymous.entity.GuestSession;
import com.longfeng.anonymous.support.SnowflakeIdGenerator;
import com.longfeng.common.exception.BusinessException;
import com.longfeng.common.exception.ErrCode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * GuestSession domain service · TDD §3.1 session/ · plan §5.S2 BE-05.
 *
 * <p><strong>State machine</strong>:
 * {@code CREATED(0) → ANALYZING(1) → RESULT_READY(2) → CLAIMED(4)}
 *                                  {@code → FAILED(3)}
 * Any state → {@code EXPIRED(9)} (by GuestSessionExpiryJob or TTL check).
 *
 * <p>All transitions use CAS UPDATE on {@code version} (D-State).
 * C3 RED LINE: this service never touches any {@code wb_*} table.
 */
@Service
@Transactional
public class GuestSessionService {

  private static final Logger log = LoggerFactory.getLogger(GuestSessionService.class);
  private static final long TTL_HOURS = 24L;

  private final GuestSessionRepository repo;
  private final SnowflakeIdGenerator idGen;

  public GuestSessionService(GuestSessionRepository repo, SnowflakeIdGenerator idGen) {
    this.repo = repo;
    this.idGen = idGen;
  }

  // ── Create ───────────────────────────────────────────────────────────────

  /**
   * Creates a new guest session with 24-h TTL.
   *
   * @param deviceFp device fingerprint (5-source composite hash)
   * @param ipHash   HMAC-SHA256 of client IP
   * @param ua       raw User-Agent string (truncated to 256 chars)
   * @param entrySource ad / qr / share / direct
   * @param experimentBucket A/B bucket label
   * @return the persisted {@link GuestSession}
   */
  public GuestSession create(
      String deviceFp, String ipHash, String ua, String entrySource, String experimentBucket) {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    GuestSession session = new GuestSession();
    session.setId(idGen.nextId());
    session.setDeviceFp(deviceFp);
    session.setIpHash(ipHash);
    session.setUa(ua != null && ua.length() > 256 ? ua.substring(0, 256) : ua);
    session.setEntrySource(entrySource);
    session.setExperimentBucket(experimentBucket);
    session.setStatus(GuestSession.STATUS_CREATED);
    session.setExpiresAt(now.plusHours(TTL_HOURS));
    GuestSession saved = repo.save(session);
    log.info("guest-session created id={} fp={}", saved.getId(), deviceFp);
    return saved;
  }

  // ── Query ────────────────────────────────────────────────────────────────

  /**
   * Returns a session by ID, or empty if not found.
   */
  @Transactional(readOnly = true)
  public Optional<GuestSession> findById(Long id) {
    return repo.findById(id);
  }

  /**
   * Returns a session by ID, throwing {@code NOT_FOUND} if absent.
   */
  @Transactional(readOnly = true)
  public GuestSession getOrThrow(Long id) {
    return repo.findById(id)
        .orElseThrow(() -> new BusinessException(ErrCode.NOT_FOUND,
            "msgkey:anon.guest.session_not_found"));
  }

  // ── Transitions ──────────────────────────────────────────────────────────

  /**
   * CAS transition: CREATED → ANALYZING.
   * Atomically moves the session to the ANALYZING state if it is still CREATED and unexpired.
   *
   * @throws BusinessException GUEST_SESSION_EXPIRED if the session has expired
   * @throws BusinessException INTERNAL_ERROR if the CAS update fails (concurrent modification)
   */
  public GuestSession startAnalyzing(Long id) {
    GuestSession session = getOrThrow(id);
    guardNotExpired(session);
    int rows = repo.casTransition(
        id,
        GuestSession.STATUS_CREATED,
        GuestSession.STATUS_ANALYZING,
        session.getVersion());
    if (rows == 0) {
      throw new BusinessException(ErrCode.INTERNAL_ERROR,
          "msgkey:anon.guest.session_cas_conflict");
    }
    log.debug("guest-session {} CREATED→ANALYZING", id);
    return repo.findById(id).orElseThrow();
  }

  /**
   * CAS transition: ANALYZING → RESULT_READY.
   * Records the AI structured-result JSON snapshot atomically with the status change in one SQL
   * statement (avoids spurious @Version increment from a prior JPA save).
   */
  public GuestSession markResultReady(Long id, String analysisResultJson) {
    GuestSession session = getOrThrow(id);
    guardNotExpired(session);
    int rows = repo.casMarkResultReady(id, analysisResultJson, session.getVersion());
    if (rows == 0) {
      throw new BusinessException(ErrCode.INTERNAL_ERROR,
          "msgkey:anon.guest.session_cas_conflict");
    }
    log.debug("guest-session {} ANALYZING→RESULT_READY", id);
    return repo.findById(id).orElseThrow();
  }

  /**
   * CAS transition: ANALYZING → FAILED.
   */
  public void markFailed(Long id) {
    GuestSession session = getOrThrow(id);
    int rows = repo.casTransition(
        id,
        GuestSession.STATUS_ANALYZING,
        GuestSession.STATUS_FAILED,
        session.getVersion());
    if (rows == 0) {
      log.warn("guest-session {} markFailed CAS conflict — already transitioned", id);
    }
  }

  /**
   * Idempotent claim: atomically moves ANALYZING/RESULT_READY → CLAIMED.
   *
   * <p>If the session is already CLAIMED (D-Guest-Claim idempotency), returns the original
   * {@code claimedQuestionId}.
   *
   * @param id        guest session ID
   * @param studentId the student claiming the session (must pass device-fp check by caller)
   * @param questionId the newly created wb_question ID (assigned by wrongbook-service caller)
   * @return the question ID that was claimed
   * @throws BusinessException GUEST_ALREADY_CLAIMED (409) if session was previously claimed
   *                           by a different student (device mismatch)
   * @throws BusinessException GUEST_SESSION_EXPIRED (410) if TTL elapsed
   */
  public Long claim(Long id, Long studentId, Long questionId) {
    GuestSession session = getOrThrow(id);

    // Idempotent: already claimed by the same student
    if (session.getStatus() == GuestSession.STATUS_CLAIMED) {
      if (studentId.equals(session.getClaimedByStudentId())) {
        log.info("guest-session {} already claimed by student {} — idempotent return", id, studentId);
        return session.getClaimedQuestionId();
      }
      throw new BusinessException(ErrCode.GUEST_ALREADY_CLAIMED,
          "msgkey:anon.guest.already_claimed");
    }

    guardNotExpired(session);

    int rows = repo.casClaim(id, studentId, questionId, session.getVersion());
    if (rows == 0) {
      // Re-read to determine whether it was claimed by another or expired
      GuestSession refreshed = repo.findById(id).orElseThrow();
      if (refreshed.getStatus() == GuestSession.STATUS_CLAIMED) {
        if (studentId.equals(refreshed.getClaimedByStudentId())) {
          return refreshed.getClaimedQuestionId();
        }
        throw new BusinessException(ErrCode.GUEST_ALREADY_CLAIMED,
            "msgkey:anon.guest.already_claimed");
      }
      if (refreshed.getStatus() == GuestSession.STATUS_EXPIRED) {
        throw new BusinessException(ErrCode.GUEST_SESSION_EXPIRED,
            "msgkey:anon.guest.session_expired");
      }
      throw new BusinessException(ErrCode.INTERNAL_ERROR,
          "msgkey:anon.guest.session_cas_conflict");
    }
    log.info("guest-session {} claimed by student={} qid={}", id, studentId, questionId);
    return questionId;
  }

  // ── Internal helpers ─────────────────────────────────────────────────────

  private void guardNotExpired(GuestSession session) {
    if (session.getStatus() == GuestSession.STATUS_EXPIRED
        || OffsetDateTime.now(ZoneOffset.UTC).isAfter(session.getExpiresAt())) {
      throw new BusinessException(ErrCode.GUEST_SESSION_EXPIRED,
          "msgkey:anon.guest.session_expired");
    }
  }
}
