package com.longfeng.anonymous.observer;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.TimeUnit;
import com.longfeng.anonymous.entity.ObserverSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Periodic GC job that expires stale observer sessions and syncs revocations to Redis.
 *
 * <p>Schedule: every 1 hour (TDD §3.1 job/ / §7.2 observer-session-gc).
 * ShedLock: 5-minute lock prevents parallel runs across pods.
 *
 * <p>Steps per run:
 * <ol>
 *   <li>Log expiring sessions for observability.
 *   <li>Bulk-expire: UPDATE status = EXPIRED where expires_at &lt; now AND status = ACTIVE.
 *   <li>Write {@code obs:revoked:{jti}} to Redis for expired sessions so the gateway can
 *       block lingering JWT presentations within ≤ 1 s (D-Observer-Revoke).
 * </ol>
 *
 * <p>C3 RED LINE: only touches {@code anon.observer_session} — never {@code wb_*}.
 */
@Component
public class ObserverSessionGcJob {

  private static final Logger log = LoggerFactory.getLogger(ObserverSessionGcJob.class);
  private static final int LOG_BATCH_LIMIT = 500;

  static final String OBS_REVOKE_KEY_PREFIX = "obs:revoked:";

  private final ObserverSessionRepository sessionRepo;
  private final StringRedisTemplate redis;

  public ObserverSessionGcJob(ObserverSessionRepository sessionRepo, StringRedisTemplate redis) {
    this.sessionRepo = sessionRepo;
    this.redis = redis;
  }

  /**
   * Main GC sweep. Runs every hour, protected by ShedLock (5 min window).
   */
  @Scheduled(cron = "0 0 * * * ?")
  // TODO S2.5: 加 @SchedulerLock(name = "observer-session-gc", lockAtMostFor = "PT5M") 防多副本竞争
  // 需要 anonymous-service pom 引入 shedlock-spring 依赖（common 模块已配 ShedLockConfig · 缺 dependency 传递）
  @Transactional
  public int sweep() {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

    // 1. Fetch a batch of expiring sessions for Redis sync before bulk-expire
    List<ObserverSession> expiring = sessionRepo.findExpiringBatch(now, LOG_BATCH_LIMIT);
    if (!expiring.isEmpty()) {
      log.info("observer-session-gc: {} sessions expiring, syncing Redis revocation keys",
          expiring.size());
      for (ObserverSession s : expiring) {
        writeRevocationToRedis(s.getJti(), s.getExpiresAt(), now);
      }
    }

    // 2. Bulk expire
    int expired = sessionRepo.expireBefore(now);
    log.info("observer-session-gc sweep complete: expired={}", expired);
    return expired;
  }

  // ── Internal ──────────────────────────────────────────────────────────────

  private void writeRevocationToRedis(String jti, OffsetDateTime expiresAt, OffsetDateTime now) {
    java.time.Duration remaining = java.time.Duration.between(now, expiresAt);
    long ttlSeconds = remaining.isNegative() ? 60L : remaining.getSeconds() + 60L;
    try {
      redis.opsForValue().set(OBS_REVOKE_KEY_PREFIX + jti, "1", ttlSeconds, TimeUnit.SECONDS);
    } catch (Exception ex) {
      log.warn("observer-session-gc: Redis write failed for jti={}: {}", jti, ex.getMessage());
    }
  }
}
