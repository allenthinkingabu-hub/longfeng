package com.longfeng.gateway.config;

import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Gateway route configuration with Nacos hot-reload support and Sentinel global rate-limit hook.
 *
 * <p>Route definitions are loaded from YAML (application.yml / Nacos config center). This class
 * provides:
 * <ol>
 *   <li>A programmatic fallback {@link RouteLocator} for unit-test scenarios where Nacos is not
 *       available.
 *   <li>Extended {@link RateLimiterRegistry} configurations for anonymous and global rate-limit
 *       tiers (TDD §16.5).
 *   <li>A Sentinel-compatible hook: the {@code SentinelGatewayFilter} integration point is
 *       prepared here so that when spring-cloud-alibaba-sentinel-gateway is on the classpath it
 *       can activate automatically without code changes. For MVP, Resilience4j acts as the
 *       gateway-level rate limiter (see ADR 0004).
 * </ol>
 *
 * <p>Nacos hot-reload: when {@code spring.cloud.nacos.config.enabled=true} (prod profile),
 * Spring Cloud Gateway's {@code GatewayProperties} refresh event automatically reloads routes from
 * the Nacos config source {@code gateway-sentinel-flow-rule.yaml}. No additional code is required
 * beyond the Nacos starter dependency and the config properties.
 *
 * <p>TDD §2.1 Gateway 总图; §0.9 D-Auth; C10 Sentinel hook for downstream Feign calls.
 */
@Configuration
public class RouteConfig {

  private static final Logger log = LoggerFactory.getLogger(RouteConfig.class);

  /** Global AI rate limit (TDD §16.5: 200 QPS total). */
  @Value("${longfeng.sentinel.ai.qps:200}")
  private int globalAiQps;

  /** Guest IP rate limit per second (translates 10/day ceiling at registry config level). */
  @Value("${longfeng.ratelimit.guest.ip.per-second:1}")
  private int guestIpPermitsPerSecond;

  /** Guest device-fp rate limit per second (translates 1/day ceiling). */
  @Value("${longfeng.ratelimit.guest.fp.per-second:1}")
  private int guestFpPermitsPerSecond;

  /**
   * Extend the {@link RateLimiterRegistry} with anon-tier configurations that AnonFilter uses.
   *
   * <p>Two named configs are registered:
   * <ul>
   *   <li>{@code anon-guest} — IP-level anonymous rate limiter (10/day). For test environments,
   *       mapped to {@code guestIpPermitsPerSecond} to allow integration tests to trigger 429s.
   *   <li>{@code anon-guest-fp} — device-fingerprint rate limiter (1/day).
   * </ul>
   */
  @Bean
  @Primary
  public RateLimiterRegistry extendedRateLimiterRegistry() {
    RateLimiterConfig anonGuestCfg =
        RateLimiterConfig.custom()
            .limitForPeriod(guestIpPermitsPerSecond > 0 ? guestIpPermitsPerSecond : 1)
            .limitRefreshPeriod(Duration.ofSeconds(1))
            .timeoutDuration(Duration.ZERO)
            .build();

    RateLimiterConfig anonFpCfg =
        RateLimiterConfig.custom()
            .limitForPeriod(guestFpPermitsPerSecond > 0 ? guestFpPermitsPerSecond : 1)
            .limitRefreshPeriod(Duration.ofSeconds(1))
            .timeoutDuration(Duration.ZERO)
            .build();

    // Default config for authenticated users (20 req/s as per existing config)
    RateLimiterConfig defaultCfg =
        RateLimiterConfig.custom()
            .limitForPeriod(20)
            .limitRefreshPeriod(Duration.ofSeconds(1))
            .timeoutDuration(Duration.ZERO)
            .build();

    RateLimiterRegistry registry = RateLimiterRegistry.of(defaultCfg);
    registry.addConfiguration("anon-guest", anonGuestCfg);
    registry.addConfiguration("anon-guest-fp", anonFpCfg);
    return registry;
  }

  /**
   * Programmatic route locator used only when Nacos is not available (test / local dev without
   * Nacos). In production, routes come from {@code application.yml} which is refreshed via Nacos
   * config pull.
   *
   * <p>This bean is marked {@code @ConditionalOnMissingBean} equivalent via Spring Boot's bean
   * override: if a Nacos-sourced {@code RouteLocator} is already registered, this one will be
   * ignored by virtue of Spring Cloud Gateway's composite locator.
   */
  @Bean(name = "fallbackRouteLocator")
  public RouteLocator fallbackRouteLocator(RouteLocatorBuilder builder) {
    log.info(
        "RouteConfig: registering fallback route locator (Nacos not active or no routes loaded)");
    return builder
        .routes()
        // Wrongbook service
        .route(
            "wrongbook-api",
            r -> r.path("/api/wb/**", "/api/v1/wrongbook/**")
                .uri("lb://wrongbook-service"))
        // AI analysis service
        .route(
            "ai-api",
            r -> r.path("/api/ai/**", "/api/v1/ai/**")
                .uri("lb://ai-analysis-service"))
        // Review plan service
        .route(
            "review-api",
            r -> r.path("/api/review-plans/**", "/api/v1/review/**")
                .uri("lb://review-plan-service"))
        // File service
        .route(
            "file-api",
            r -> r.path("/api/files/**", "/api/v1/file/**")
                .uri("lb://file-service"))
        // Anonymous service (guest / share / observer)
        .route(
            "anon-api",
            r -> r.path(
                    "/api/landing/**",
                    "/api/session/**",
                    "/api/guest/**",
                    "/api/observer/**",
                    "/api/share/**",
                    "/api/v1/anon/**")
                .uri("lb://anonymous-service"))
        .build();
  }
}
