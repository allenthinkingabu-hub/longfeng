package com.longfeng.gateway.config;

import io.github.resilience4j.ratelimiter.RateLimiterConfig.Builder;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Resilience4j rate limiter registry · 落地计划 §6.6.
 *
 * <p>Default bucket: 20 permits / second · no wait (immediate 429). Per-client instances are
 * created on demand via {@code registry.rateLimiter(key, "default")} inside
 * {@code RateLimitFilter}.
 */
@Configuration
public class RateLimiterConfig {

  @Bean
  public RateLimiterRegistry rateLimiterRegistry() {
    Builder cfg = io.github.resilience4j.ratelimiter.RateLimiterConfig.custom()
        .limitForPeriod(20)
        .limitRefreshPeriod(Duration.ofSeconds(1))
        .timeoutDuration(Duration.ZERO);
    return RateLimiterRegistry.of(cfg.build());
  }
}
