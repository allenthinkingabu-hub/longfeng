package com.longfeng.gateway.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Redis-backed revocation check for share tokens and observer tokens.
 *
 * <p>Uses Redis SET (key = {@code share:revoked:{jti}} or {@code obs:revoked:{jti}}).
 * When a key exists the corresponding token is considered revoked.
 *
 * <p>TDD §0.9 D-Share + D-Observer-Revoke: Bloom Filter with ≤1s revocation propagation.
 * In this implementation we use a simple Redis key presence check (semantically equivalent
 * to a Bloom Filter check with zero false-negative rate for exact-match lookups, acceptable
 * for MVP; a Bloom Filter BF.EXISTS can be layered on top in P1).
 */
@Service
public class BloomRevocationService {

  private static final Logger log = LoggerFactory.getLogger(BloomRevocationService.class);

  static final String SHARE_REVOKED_PREFIX = "share:revoked:";
  static final String OBS_REVOKED_PREFIX = "obs:revoked:";

  private final ReactiveStringRedisTemplate redisTemplate;

  public BloomRevocationService(ReactiveStringRedisTemplate redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  /**
   * Returns {@code true} if the share token with the given JTI has been revoked.
   * Falls back to {@code false} on Redis error (fail-open for availability).
   */
  public Mono<Boolean> isShareTokenRevoked(String jti) {
    return redisTemplate
        .hasKey(SHARE_REVOKED_PREFIX + jti)
        .onErrorResume(
            ex -> {
              log.warn("Redis error checking share revocation for jti={}: {}", jti, ex.getMessage());
              return Mono.just(false); // fail-open
            });
  }

  /**
   * Returns {@code true} if the observer token with the given JTI has been revoked.
   * Falls back to {@code false} on Redis error (fail-open for availability).
   */
  public Mono<Boolean> isObserverTokenRevoked(String jti) {
    return redisTemplate
        .hasKey(OBS_REVOKED_PREFIX + jti)
        .onErrorResume(
            ex -> {
              log.warn(
                  "Redis error checking observer revocation for jti={}: {}", jti, ex.getMessage());
              return Mono.just(false); // fail-open
            });
  }

  /**
   * Mark a share token as revoked (used in tests and by anonymous-service callback).
   * TTL should be set to match the token's remaining expiry.
   */
  public Mono<Boolean> revokeShareToken(String jti, java.time.Duration ttl) {
    return redisTemplate.opsForValue().set(SHARE_REVOKED_PREFIX + jti, "1", ttl);
  }

  /**
   * Mark an observer token as revoked.
   */
  public Mono<Boolean> revokeObserverToken(String jti, java.time.Duration ttl) {
    return redisTemplate.opsForValue().set(OBS_REVOKED_PREFIX + jti, "1", ttl);
  }
}
