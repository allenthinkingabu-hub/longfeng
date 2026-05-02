package com.longfeng.anonymous.ratelimit;

import java.time.LocalDate;
import java.time.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * RateLimitFallbackToDb · DB-backed rate limiting fallback · plan §5.S2 BE-05.
 *
 * <p>When Redis is unavailable, {@link GuestRateLimiter} falls back to this component which
 * atomically increments counters in {@code anon.guest_rate_bucket} using an upsert
 * (INSERT ... ON CONFLICT DO UPDATE).
 *
 * <p>Limits mirror those enforced in Redis:
 * <ul>
 *   <li>device_fp: 1 / natural day (Asia/Shanghai)</li>
 *   <li>ip_hash:  10 / natural day (Asia/Shanghai)</li>
 * </ul>
 *
 * <p>C3 RED LINE: only touches {@code anon.guest_rate_bucket} — never {@code wb_*}.
 */
@Component
public class RateLimitFallbackToDb {

  private static final Logger log = LoggerFactory.getLogger(RateLimitFallbackToDb.class);
  private static final ZoneId CST = ZoneId.of("Asia/Shanghai");

  private static final int FP_LIMIT = 1;
  private static final int IP_LIMIT = 10;

  private final GuestRateBucketRepository bucketRepo;

  public RateLimitFallbackToDb(GuestRateBucketRepository bucketRepo) {
    this.bucketRepo = bucketRepo;
  }

  /**
   * Attempts to consume a device-fingerprint quota slot for today.
   * Uses a new transaction to ensure the upsert is committed even when the caller rolls back.
   *
   * @param deviceFp device fingerprint hash
   * @param ipHash   ip hash (used as part of composite PK; pass empty string for fp-only checks)
   * @param day      date in Asia/Shanghai timezone
   * @return {@code true} if within limit, {@code false} if limit exceeded
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean tryConsumeFp(String deviceFp, String ipHash, LocalDate day) {
    bucketRepo.upsertIncrement(deviceFp, ipHash, day);
    Integer count = bucketRepo.findCount(deviceFp, ipHash, day);
    boolean allowed = count != null && count <= FP_LIMIT;
    if (!allowed) {
      log.info("DB fallback fp limit reached fp={} date={} count={}", deviceFp, day, count);
    }
    return allowed;
  }

  /**
   * Attempts to consume an IP quota slot for today.
   * Uses a shared ip-aggregation key (deviceFp="IP_AGG").
   *
   * @param ipHash ip address hash
   * @param day    date in Asia/Shanghai timezone
   * @return {@code true} if within limit, {@code false} if limit exceeded
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean tryConsumeIp(String ipHash, LocalDate day) {
    // Use a fixed marker for device_fp when aggregating by IP
    String aggregationFp = "IP_AGG";
    bucketRepo.upsertIncrement(aggregationFp, ipHash, day);
    Integer count = bucketRepo.findCount(aggregationFp, ipHash, day);
    boolean allowed = count != null && count <= IP_LIMIT;
    if (!allowed) {
      log.info("DB fallback ip limit reached ipHash={} date={} count={}", ipHash, day, count);
    }
    return allowed;
  }

  /**
   * Convenience factory for today's date in CST.
   */
  static LocalDate todayCst() {
    return LocalDate.now(CST);
  }
}
