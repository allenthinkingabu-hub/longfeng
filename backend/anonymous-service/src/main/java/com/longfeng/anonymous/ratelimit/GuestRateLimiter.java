package com.longfeng.anonymous.ratelimit;

import com.longfeng.common.exception.BusinessException;
import com.longfeng.common.exception.ErrCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * GuestRateLimiter · Bucket4j-style dual-dimension rate limiting · plan §5.S2 BE-05.
 *
 * <p>Dual limits (D-Guest-Quota):
 * <ul>
 *   <li>{@code device_fp}: 1 analysis request / natural day</li>
 *   <li>{@code ip_hash}: 10 analysis requests / natural day</li>
 * </ul>
 *
 * <p>Primary backend: Redis via {@link StringRedisTemplate} (INCR + EXPIRE pattern).
 * Fallback backend: PostgreSQL {@code anon.guest_rate_bucket} via {@link RateLimitFallbackToDb}
 * when Redis is unavailable.
 *
 * <p>C3 RED LINE: no {@code wb_*} tables referenced directly.
 */
@Component
public class GuestRateLimiter {

  private static final Logger log = LoggerFactory.getLogger(GuestRateLimiter.class);

  private static final int FP_LIMIT_PER_DAY = 1;
  private static final int IP_LIMIT_PER_DAY = 10;
  private static final ZoneId CST = ZoneId.of("Asia/Shanghai");

  private final StringRedisTemplate redis;
  private final RateLimitFallbackToDb fallbackToDb;

  @Value("${anon.ratelimit.fp-prefix:rate:guest:fp:}")
  private String fpKeyPrefix;

  @Value("${anon.ratelimit.ip-prefix:rate:guest:ip:}")
  private String ipKeyPrefix;

  public GuestRateLimiter(StringRedisTemplate redis, RateLimitFallbackToDb fallbackToDb) {
    this.redis = redis;
    this.fallbackToDb = fallbackToDb;
  }

  /**
   * Checks and consumes one analysis quota token for the given device + IP pair.
   *
   * <p>The check-and-consume is NOT atomic between fp and ip dimensions (two separate Redis ops),
   * but each dimension individually is atomic via INCR. In the rare case where the ip limit passes
   * but a concurrent request consumes the fp slot, the worst outcome is an over-count of 1 — which
   * is acceptable per D-Guest-Quota ("纯 device_fp 容易被刷，加 IP 维度兜底").
   *
   * @param deviceFp 5-source composite fingerprint hash
   * @param rawIp    client IP address (will be hashed before storage)
   * @throws BusinessException GUEST_QUOTA_EXHAUSTED (429) when either limit is exceeded
   */
  @Transactional
  public void checkAndConsume(String deviceFp, String rawIp) {
    String ipHash = hmacIp(rawIp);
    LocalDate today = LocalDate.now(CST);

    boolean fpAllowed = tryConsumeFp(deviceFp, today);
    if (!fpAllowed) {
      log.info("guest rate-limit EXCEEDED fp={} date={}", deviceFp, today);
      throw new BusinessException(ErrCode.GUEST_QUOTA_EXHAUSTED,
          "msgkey:anon.guest.quota_exhausted");
    }

    boolean ipAllowed = tryConsumeIp(ipHash, today);
    if (!ipAllowed) {
      // Rollback the fp consume (best-effort compensation)
      rollbackFp(deviceFp, today);
      log.info("guest rate-limit EXCEEDED ip-hash={} date={}", ipHash, today);
      throw new BusinessException(ErrCode.GUEST_QUOTA_EXHAUSTED,
          "msgkey:anon.guest.quota_exhausted");
    }
  }

  // ── fp dimension ──────────────────────────────────────────────────────────

  private boolean tryConsumeFp(String deviceFp, LocalDate day) {
    String key = fpKeyPrefix + deviceFp + ":" + day;
    try {
      Long count = redis.opsForValue().increment(key);
      if (count == 1L) {
        // First increment — set TTL to 25 hours to survive DST edge + next day
        redis.expire(key, Duration.ofHours(25));
      }
      return count != null && count <= FP_LIMIT_PER_DAY;
    } catch (Exception ex) {
      log.warn("Redis unavailable for fp rate-limit — falling back to DB: {}", ex.getMessage());
      return fallbackToDb.tryConsumeFp(deviceFp, hmacIp(""), day);
    }
  }

  private void rollbackFp(String deviceFp, LocalDate day) {
    try {
      String key = fpKeyPrefix + deviceFp + ":" + day;
      redis.opsForValue().decrement(key);
    } catch (Exception ex) {
      log.warn("Failed to rollback fp rate-limit counter: {}", ex.getMessage());
    }
  }

  // ── ip dimension ──────────────────────────────────────────────────────────

  private boolean tryConsumeIp(String ipHash, LocalDate day) {
    String key = ipKeyPrefix + ipHash + ":" + day;
    try {
      Long count = redis.opsForValue().increment(key);
      if (count == 1L) {
        redis.expire(key, Duration.ofHours(25));
      }
      return count != null && count <= IP_LIMIT_PER_DAY;
    } catch (Exception ex) {
      log.warn("Redis unavailable for ip rate-limit — falling back to DB: {}", ex.getMessage());
      return fallbackToDb.tryConsumeIp(ipHash, day);
    }
  }

  // ── IP hashing ────────────────────────────────────────────────────────────

  /**
   * Hashes the raw IP address using SHA-256. We do not store raw IPs (GDPR / privacy).
   * A proper HMAC-SHA256 would require a stable secret; for rate-limiting purposes,
   * plain SHA-256 is sufficient since we only need bucketing, not cryptographic binding.
   */
  static String hmacIp(String rawIp) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] digest = md.digest(safe(rawIp).getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest).substring(0, 32); // 32 hex chars = 16 bytes
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }

  private static String safe(String s) {
    return s == null ? "" : s;
  }
}
