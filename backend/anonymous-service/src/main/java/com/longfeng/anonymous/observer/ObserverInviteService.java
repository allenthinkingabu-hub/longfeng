package com.longfeng.anonymous.observer;

import com.longfeng.anonymous.entity.ObserverInvite;
import com.longfeng.anonymous.entity.ObserverSession;
import com.longfeng.anonymous.support.SnowflakeIdGenerator;
import com.longfeng.common.exception.BusinessException;
import com.longfeng.common.exception.ErrCode;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ObserverInviteService — generates observer invite codes and exchanges them for sessions.
 *
 * <p>TDD §3.1 observer/ + D-Observer-TTL:
 * <ul>
 *   <li>Generates 6-character uppercase alphanumeric invite codes (24-h TTL).
 *   <li>Exchanges a PENDING invite code for an {@link ObserverSession} with role-based TTL:
 *       PARENT → 30 days · TEACHER → 90 days.
 * </ul>
 *
 * <p>C4 RED LINE: every issued ObserverSession carries {@code scope=READ} in the JWT claims.
 *   The service never constructs a JWT with scope != READ.
 *
 * <p>C3 RED LINE: never reads/writes {@code wb_*} tables.
 * C8: all {@link BusinessException} carry {@code msgkey:} prefix.
 */
@Service
@Transactional
public class ObserverInviteService {

  private static final Logger log = LoggerFactory.getLogger(ObserverInviteService.class);

  /** Observer invite valid for 24 hours. */
  static final long INVITE_TTL_HOURS = 24L;

  /** D-Observer-TTL: PARENT role session TTL. */
  static final long PARENT_SESSION_DAYS = 30L;

  /** D-Observer-TTL: TEACHER role session TTL. */
  static final long TEACHER_SESSION_DAYS = 90L;

  /** Alphabet for invite code generation (uppercase alphanumeric, exclude ambiguous O/0/I/1). */
  private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  private static final int CODE_LENGTH = 6;

  private final ObserverInviteRepository inviteRepo;
  private final ObserverSessionRepository sessionRepo;
  private final SnowflakeIdGenerator idGen;

  @Value("${longfeng.jwt.anon.secret:changeme-anon-secret-min32chars!!!}")
  private String anonSecret;

  public ObserverInviteService(
      ObserverInviteRepository inviteRepo,
      ObserverSessionRepository sessionRepo,
      SnowflakeIdGenerator idGen) {
    this.inviteRepo = inviteRepo;
    this.sessionRepo = sessionRepo;
    this.idGen = idGen;
  }

  // ── Generate invite code ─────────────────────────────────────────────────

  /**
   * Generates a new observer invite for a student with the given role.
   *
   * <p>The invite code is a 6-character string drawn from a 32-character uppercase alphabet
   * (avoids visually ambiguous O/0/I/1). Collisions are retried up to 5 times.
   *
   * @param studentId the student issuing the invite
   * @param role      PARENT or TEACHER
   * @return the persisted {@link ObserverInvite}
   * @throws BusinessException VALIDATION_FAILED if role is invalid
   */
  public ObserverInvite generateInvite(Long studentId, String role) {
    if (!ObserverInvite.ROLE_PARENT.equals(role) && !ObserverInvite.ROLE_TEACHER.equals(role)) {
      throw new BusinessException(ErrCode.VALIDATION_FAILED,
          "msgkey:anon.observer.invalid_role");
    }

    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    OffsetDateTime expiresAt = now.plusHours(INVITE_TTL_HOURS);

    String inviteCode = generateUniqueCode();

    ObserverInvite invite = new ObserverInvite();
    invite.setId(idGen.nextId());
    invite.setInviteCode(inviteCode);
    invite.setStudentId(studentId);
    invite.setRole(role);
    invite.setStatus(ObserverInvite.STATUS_PENDING);
    invite.setExpiresAt(expiresAt);

    ObserverInvite saved = inviteRepo.save(invite);
    log.info("observer-invite generated code={} student={} role={}", inviteCode, studentId, role);
    return saved;
  }

  // ── Exchange invite for session ──────────────────────────────────────────

  /**
   * Exchanges a PENDING invite code for an {@link ObserverSession}.
   *
   * <p>Steps:
   * <ol>
   *   <li>Find PENDING invite by code; throw TOKEN_EXPIRED if not found or expired.
   *   <li>Atomically mark invite EXCHANGED (CAS).
   *   <li>Create ObserverSession with role-based TTL (D-Observer-TTL).
   *   <li>Issue HS256 JWT with {@code scope=READ} (C4).
   * </ol>
   *
   * @param inviteCode    the 6-char code provided by the observer
   * @param observerDeviceFp observer's device fingerprint (optional)
   * @return the raw HS256 observer JWT
   * @throws BusinessException TOKEN_EXPIRED if invite not found, expired, or already used
   */
  public String exchange(String inviteCode, String observerDeviceFp) {
    // 1. Find PENDING invite
    ObserverInvite invite = inviteRepo.findPendingByCode(inviteCode)
        .orElseThrow(() -> new BusinessException(ErrCode.TOKEN_EXPIRED,
            "msgkey:anon.observer.invite_not_found"));

    // Check wall-clock expiry (DB status check is by query predicate, but double-check)
    if (OffsetDateTime.now(ZoneOffset.UTC).isAfter(invite.getExpiresAt())) {
      throw new BusinessException(ErrCode.TOKEN_EXPIRED,
          "msgkey:anon.observer.invite_expired");
    }

    // 2. CAS mark EXCHANGED
    int rows = inviteRepo.casMarkExchanged(invite.getId());
    if (rows == 0) {
      throw new BusinessException(ErrCode.TOKEN_EXPIRED,
          "msgkey:anon.observer.invite_already_used");
    }

    // 3. Determine session TTL (D-Observer-TTL)
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    long ttlDays = ObserverInvite.ROLE_TEACHER.equals(invite.getRole())
        ? TEACHER_SESSION_DAYS
        : PARENT_SESSION_DAYS;
    OffsetDateTime sessionExpiresAt = now.plusDays(ttlDays);

    // 4. Create ObserverSession (C4: scope must be READ)
    String jti = UUID.randomUUID().toString().replace("-", "");

    ObserverSession session = new ObserverSession();
    session.setId(idGen.nextId());
    session.setJti(jti);
    session.setStudentId(invite.getStudentId());
    session.setRole(invite.getRole());
    session.setDeviceFp(observerDeviceFp);
    session.setStatus(ObserverSession.STATUS_ACTIVE);
    session.setLastSeenAt(now);
    session.setExpiresAt(sessionExpiresAt);
    sessionRepo.save(session);

    // 5. Issue HS256 JWT with scope=READ (C4 RED LINE)
    String rawJwt;
    try {
      JWSSigner signer = new MACSigner(anonSecret.getBytes(StandardCharsets.UTF_8));
      JWTClaimsSet claims = new JWTClaimsSet.Builder()
          .jwtID(jti)
          .subject(String.valueOf(invite.getStudentId()))
          .claim("scope", ObserverSession.SCOPE_READ)  // C4: always READ
          .claim("role", invite.getRole())
          .expirationTime(Date.from(sessionExpiresAt.toInstant()))
          .issueTime(Date.from(now.toInstant()))
          .build();
      SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
      jwt.sign(signer);
      rawJwt = jwt.serialize();
    } catch (Exception e) {
      log.error("Failed to sign observer JWT for invite code={}: {}", inviteCode, e.getMessage());
      throw new BusinessException(ErrCode.INTERNAL_ERROR,
          "msgkey:anon.observer.sign_failed");
    }

    log.info("observer-invite exchanged code={} student={} role={} jti={} ttlDays={}",
        inviteCode, invite.getStudentId(), invite.getRole(), jti, ttlDays);
    return rawJwt;
  }

  // ── Internal helpers ─────────────────────────────────────────────────────

  /**
   * Generates a 6-character uppercase alphanumeric invite code from the configured alphabet,
   * retrying up to 5 times on uniqueness collision.
   *
   * <p>The alphabet excludes visually ambiguous characters (O, 0, I, 1) to reduce
   * transcription errors when sharing codes verbally.
   *
   * @return a unique 6-character invite code
   * @throws BusinessException INTERNAL_ERROR if uniqueness cannot be guaranteed after 5 tries
   */
  private String generateUniqueCode() {
    for (int attempt = 0; attempt < 5; attempt++) {
      String candidate = randomCode();
      if (inviteRepo.findByInviteCode(candidate).isEmpty()) {
        return candidate;
      }
      log.debug("observer invite code collision on '{}', retrying ({}/5)", candidate, attempt + 1);
    }
    throw new BusinessException(ErrCode.INTERNAL_ERROR,
        "msgkey:anon.observer.code_generation_failed");
  }

  private String randomCode() {
    UUID uuid = UUID.randomUUID();
    long bits = uuid.getMostSignificantBits();
    char[] code = new char[CODE_LENGTH];
    int alphabetSize = CODE_ALPHABET.length();
    for (int i = 0; i < CODE_LENGTH; i++) {
      code[i] = CODE_ALPHABET.charAt((int) (Math.abs(bits) % alphabetSize));
      bits = bits >>> 5;
    }
    return new String(code);
  }
}
