package com.longfeng.anonymous.share;

import com.longfeng.anonymous.entity.ShareToken;
import com.longfeng.anonymous.entity.ShareTokenAudit;
import com.longfeng.anonymous.support.SnowflakeIdGenerator;
import com.longfeng.common.exception.BusinessException;
import com.longfeng.common.exception.ErrCode;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ShareTokenService — HS256 share token issuance, verification, and revocation.
 *
 * <p>Implements TDD §3.1 share/ + D-Share decision:
 * <ul>
 *   <li>Issues short-lived HS256 JWTs (≤ 7 days) signed with {@code longfeng.jwt.share.secret}.
 *   <li>Verifies signature + expiry + Redis revocation (Bloom key {@code share:revoked:{jti}}).
 *   <li>Revokes by DB update (status → REVOKED) + Redis SETEX for gateway real-time pickup.
 * </ul>
 *
 * <p>C3 RED LINE: never reads/writes {@code wb_*} tables.
 * C8: all {@link BusinessException} carry {@code msgkey:} prefix.
 */
@Service
@Transactional
public class ShareTokenService {

  private static final Logger log = LoggerFactory.getLogger(ShareTokenService.class);

  /** Maximum allowed TTL for share tokens (D-Share). */
  static final long MAX_TTL_SECONDS = 7L * 24 * 3600; // 7 days

  /** Redis key prefix for share token revocations. */
  static final String REVOKE_KEY_PREFIX = "share:revoked:";

  private final ShareTokenRepository tokenRepo;
  private final ShareTokenAuditRepository auditRepo;
  private final SnowflakeIdGenerator idGen;
  private final StringRedisTemplate redis;

  @Value("${longfeng.jwt.share.secret:changeme-share-secret-min32chars!!}")
  private String shareSecret;

  public ShareTokenService(
      ShareTokenRepository tokenRepo,
      ShareTokenAuditRepository auditRepo,
      SnowflakeIdGenerator idGen,
      StringRedisTemplate redis) {
    this.tokenRepo = tokenRepo;
    this.auditRepo = auditRepo;
    this.idGen = idGen;
    this.redis = redis;
  }

  // ── Issue ────────────────────────────────────────────────────────────────

  /**
   * Issues a new HS256 share token and persists the record.
   *
   * @param sharerStudentId the student creating the share link
   * @param shareType       EXAM_DAY / QUESTION / REVIEW_NODE
   * @param relationId      e.g. "question:42" or "review_node:99"
   * @param expiresInSec    desired TTL in seconds (capped to {@value #MAX_TTL_SECONDS})
   * @param allowClaim      whether the receiver can claim the shared resource
   * @param usageLimit      max view count before token transitions to EXHAUSTED
   * @return the raw HS256 JWT string
   */
  public String issue(
      Long sharerStudentId,
      String shareType,
      String relationId,
      long expiresInSec,
      boolean allowClaim,
      int usageLimit) {

    long effectiveTtl = Math.min(expiresInSec, MAX_TTL_SECONDS);
    if (effectiveTtl <= 0) {
      throw new BusinessException(ErrCode.VALIDATION_FAILED,
          "msgkey:anon.share.ttl_invalid");
    }

    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    OffsetDateTime expiresAt = now.plusSeconds(effectiveTtl);

    String jti = UUID.randomUUID().toString().replace("-", "");

    // Sign HS256 JWT
    String rawJwt;
    try {
      JWSSigner signer = new MACSigner(shareSecret.getBytes(StandardCharsets.UTF_8));
      JWTClaimsSet claims = new JWTClaimsSet.Builder()
          .jwtID(jti)
          .subject(String.valueOf(sharerStudentId))
          .claim("shareType", shareType)
          .claim("relationId", relationId)
          .claim("allowClaim", allowClaim)
          .expirationTime(Date.from(expiresAt.toInstant()))
          .issueTime(Date.from(now.toInstant()))
          .build();
      SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
      jwt.sign(signer);
      rawJwt = jwt.serialize();
    } catch (Exception e) {
      log.error("Failed to sign share token for student={}: {}", sharerStudentId, e.getMessage());
      throw new BusinessException(ErrCode.INTERNAL_ERROR,
          "msgkey:anon.share.sign_failed");
    }

    // Persist entity
    ShareToken token = new ShareToken();
    token.setId(idGen.nextId());
    token.setJti(jti);
    token.setSharerStudentId(sharerStudentId);
    token.setShareType(shareType);
    token.setRelationId(relationId);
    token.setAllowClaim(allowClaim);
    token.setUsageLimit(usageLimit);
    token.setUsageCount(0);
    token.setStatus(ShareToken.STATUS_ACTIVE);
    token.setExpiresAt(expiresAt);
    tokenRepo.save(token);

    log.info("share-token issued jti={} sharer={} type={}", jti, sharerStudentId, shareType);
    return rawJwt;
  }

  // ── Verify ───────────────────────────────────────────────────────────────

  /**
   * Verifies a raw JWT and returns the corresponding {@link ShareToken} if valid.
   *
   * <p>Validation steps:
   * <ol>
   *   <li>Parse and verify HS256 signature.
   *   <li>Check JWT expiry claim.
   *   <li>Load DB record; check status ACTIVE.
   *   <li>Check Redis revocation key (fail-open: unavailable Redis → proceed).
   *   <li>Increment usage_count; transition to EXHAUSTED if limit reached.
   * </ol>
   *
   * @param rawJwt the raw HS256 share token string
   * @param viewerDeviceFp viewer's device fingerprint (may be null)
   * @param viewerIpHash   HMAC-SHA256 of viewer's IP (may be null)
   * @return the verified and usage-incremented {@link ShareToken}
   * @throws BusinessException TOKEN_EXPIRED  if expired
   * @throws BusinessException NOT_FOUND      if token not in DB
   * @throws BusinessException OBSERVER_REVOKED if revoked
   */
  public ShareToken verify(String rawJwt, String viewerDeviceFp, String viewerIpHash) {
    // 1. Parse & verify HS256
    JWTClaimsSet claims;
    String jti;
    try {
      JWSVerifier verifier = new MACVerifier(shareSecret.getBytes(StandardCharsets.UTF_8));
      SignedJWT jwt = SignedJWT.parse(rawJwt);
      if (!jwt.verify(verifier)) {
        throw new BusinessException(ErrCode.NOT_FOUND,
            "msgkey:anon.share.invalid_signature");
      }
      claims = jwt.getJWTClaimsSet();
      Date exp = claims.getExpirationTime();
      if (exp == null || exp.before(new Date())) {
        throw new BusinessException(ErrCode.TOKEN_EXPIRED,
            "msgkey:anon.share.token_expired");
      }
      jti = claims.getJWTID();
    } catch (BusinessException be) {
      throw be;
    } catch (Exception e) {
      log.debug("share-token parse/verify error: {}", e.getMessage());
      throw new BusinessException(ErrCode.NOT_FOUND,
          "msgkey:anon.share.invalid_signature");
    }

    // 2. Redis revocation check (fail-open)
    boolean isRevoked = false;
    try {
      isRevoked = Boolean.TRUE.equals(redis.hasKey(REVOKE_KEY_PREFIX + jti));
    } catch (Exception ex) {
      log.warn("Redis unavailable during share revocation check for jti={}: {}", jti, ex.getMessage());
    }
    if (isRevoked) {
      throw new BusinessException(ErrCode.OBSERVER_REVOKED,
          "msgkey:anon.share.token_revoked");
    }

    // 3. Load DB record
    ShareToken token = tokenRepo.findByJti(jti)
        .orElseThrow(() -> new BusinessException(ErrCode.NOT_FOUND,
            "msgkey:anon.share.not_found"));

    // 4. DB status check
    if (token.getStatus() == ShareToken.STATUS_REVOKED) {
      throw new BusinessException(ErrCode.OBSERVER_REVOKED,
          "msgkey:anon.share.token_revoked");
    }
    if (token.getStatus() == ShareToken.STATUS_EXPIRED
        || OffsetDateTime.now(ZoneOffset.UTC).isAfter(token.getExpiresAt())) {
      throw new BusinessException(ErrCode.TOKEN_EXPIRED,
          "msgkey:anon.share.token_expired");
    }
    if (token.getStatus() == ShareToken.STATUS_EXHAUSTED) {
      throw new BusinessException(ErrCode.TOKEN_EXPIRED,
          "msgkey:anon.share.token_exhausted");
    }

    // 5. Increment usage count (ignore if 0 rows — concurrent request may have exhausted it)
    tokenRepo.incrementUsageCount(token.getId());

    // 6. Record audit
    ShareTokenAudit audit = new ShareTokenAudit();
    audit.setId(idGen.nextId());
    audit.setJti(jti);
    audit.setViewerDeviceFp(viewerDeviceFp);
    audit.setViewerIpHash(viewerIpHash);
    auditRepo.save(audit);

    log.debug("share-token verified jti={}", jti);
    return token;
  }

  // ── Revoke ───────────────────────────────────────────────────────────────

  /**
   * Revokes a share token by JTI.
   *
   * <p>Steps:
   * <ol>
   *   <li>Load token; check ownership.
   *   <li>CAS update status → REVOKED in DB.
   *   <li>Write Redis SETEX {@code share:revoked:{jti}} with remaining TTL.
   * </ol>
   *
   * @param jti              the JWT ID to revoke
   * @param requestingStudentId the student requesting revocation (must be sharer)
   * @throws BusinessException NOT_FOUND if jti not found
   * @throws BusinessException ANONYMOUS_WRITE_FORBIDDEN if not the sharer
   */
  public void revoke(String jti, Long requestingStudentId) {
    ShareToken token = tokenRepo.findByJti(jti)
        .orElseThrow(() -> new BusinessException(ErrCode.NOT_FOUND,
            "msgkey:anon.share.not_found"));

    if (!token.getSharerStudentId().equals(requestingStudentId)) {
      throw new BusinessException(ErrCode.ANONYMOUS_WRITE_FORBIDDEN,
          "msgkey:anon.share.not_owner");
    }

    if (token.getStatus() != ShareToken.STATUS_ACTIVE) {
      // Already revoked/expired/exhausted — idempotent no-op
      log.debug("share-token jti={} already inactive (status={}), revoke no-op", jti, token.getStatus());
      return;
    }

    int rows = tokenRepo.casRevoke(jti);
    if (rows == 0) {
      log.warn("share-token jti={} casRevoke returned 0 rows — concurrent modification", jti);
    }

    // Write to Redis for real-time gateway pickup (D-Share)
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    Duration remaining = Duration.between(now, token.getExpiresAt());
    if (!remaining.isNegative() && !remaining.isZero()) {
      try {
        redis.opsForValue().set(REVOKE_KEY_PREFIX + jti, "1", remaining.getSeconds(), TimeUnit.SECONDS);
        log.info("share-token jti={} revoked + Redis SETEX ttl={}s", jti, remaining.getSeconds());
      } catch (Exception ex) {
        log.warn("Redis write failed for share revocation jti={}: {}", jti, ex.getMessage());
        // Non-fatal: gateway will fallback to DB check or Bloom sync job
      }
    }
  }

  // ── Listing ──────────────────────────────────────────────────────────────

  /**
   * Returns up to 50 active share tokens for a given sharer.
   */
  @Transactional(readOnly = true)
  public List<ShareToken> listActiveBySharer(Long sharerStudentId) {
    return tokenRepo.findActiveBySharer(sharerStudentId, 50);
  }
}
