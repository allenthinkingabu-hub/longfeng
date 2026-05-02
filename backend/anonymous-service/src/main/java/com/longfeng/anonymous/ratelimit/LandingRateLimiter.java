package com.longfeng.anonymous.ratelimit;

import com.longfeng.common.exception.BusinessException;
import com.longfeng.common.exception.ErrCode;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * LandingRateLimiter · 30 requests / minute / IP · plan §5.S2 BE-05.
 *
 * <p>Second-level backend rate limiter for the landing page (Cloudflare is the first level).
 * Uses Redis sliding-window INCR + TTL pattern keyed on hashed IP.
 * If Redis is unavailable, the limiter degrades gracefully (allow-through) to avoid blocking
 * legitimate traffic due to infrastructure failures.
 *
 * <p>C3 RED LINE: no {@code wb_*} tables.
 */
@Component
public class LandingRateLimiter {

  private static final Logger log = LoggerFactory.getLogger(LandingRateLimiter.class);

  private static final int DEFAULT_LIMIT_PER_MINUTE = 30;
  private static final Duration WINDOW = Duration.ofMinutes(1);

  private final StringRedisTemplate redis;

  @Value("${anon.ratelimit.landing-prefix:rate:landing:}")
  private String keyPrefix;

  @Value("${anon.ratelimit.landing-limit:30}")
  private int limitPerMinute;

  public LandingRateLimiter(StringRedisTemplate redis) {
    this.redis = redis;
    this.limitPerMinute = DEFAULT_LIMIT_PER_MINUTE;
  }

  /**
   * Checks and records a landing page request for the given (hashed) IP.
   *
   * <p>The key is bucketed to the current UTC minute: {@code rate:landing:{ipHash}:{epochMinute}}.
   * This avoids per-key TTL drift from INCR-then-EXPIRE race.
   *
   * @param rawIp the client's raw IP address (hashed internally)
   * @throws BusinessException LANDING_RATE_LIMIT (429) if the per-minute limit is exceeded
   */
  public void checkAndConsume(String rawIp) {
    String ipHash = GuestRateLimiter.hmacIp(rawIp);
    long epochMinute = System.currentTimeMillis() / 60_000L;
    String key = keyPrefix + ipHash + ":" + epochMinute;
    try {
      Long count = redis.opsForValue().increment(key);
      if (count == 1L) {
        // First hit in this minute window — set TTL to 90s to cover edge cases
        redis.expire(key, Duration.ofSeconds(90));
      }
      if (count != null && count > limitPerMinute) {
        log.info("landing rate-limit exceeded ip-hash={} count={}", ipHash, count);
        throw new BusinessException(ErrCode.LANDING_RATE_LIMIT,
            "msgkey:anon.landing.rate_limit");
      }
    } catch (BusinessException e) {
      throw e; // re-throw rate-limit exceptions
    } catch (Exception ex) {
      // Redis unavailable — degrade to allow-through (Cloudflare is primary guard)
      log.warn("LandingRateLimiter: Redis unavailable — allow-through: {}", ex.getMessage());
    }
  }
}
