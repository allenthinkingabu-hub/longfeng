package com.longfeng.wrongbook.service;

import java.time.Duration;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Redis-backed idempotency for {@code POST /wrongbook/items}. Key {@code idem:wb:{requestId}} TTL
 * 10 min — Q-compliance note: no PII cached (only id). Falls back to no-op when Redis unavailable
 * (S3 DoR allows it; S1 V1.0.052 idem_key table backs production).
 */
@Service
public class IdempotencyService {

  private static final String KEY_PREFIX = "idem:wb:";
  private static final Duration TTL = Duration.ofMinutes(10);

  private final StringRedisTemplate redis;

  public IdempotencyService(@Autowired(required = false) StringRedisTemplate redis) {
    this.redis = redis;
  }

  public Optional<Long> peek(String requestId) {
    if (requestId == null || requestId.isBlank() || redis == null) {
      return Optional.empty();
    }
    try {
      String v = redis.opsForValue().get(KEY_PREFIX + requestId);
      return v == null ? Optional.empty() : Optional.of(Long.valueOf(v));
    } catch (Exception ignored) {
      return Optional.empty();
    }
  }

  /** Atomic SET NX EX — true when the caller owns the key and should proceed. */
  public boolean tryClaim(String requestId, Long itemId) {
    if (requestId == null || requestId.isBlank() || redis == null) {
      return true;
    }
    try {
      Boolean ok =
          redis.opsForValue().setIfAbsent(KEY_PREFIX + requestId, String.valueOf(itemId), TTL);
      return Boolean.TRUE.equals(ok);
    } catch (Exception ignored) {
      return true;
    }
  }
}
