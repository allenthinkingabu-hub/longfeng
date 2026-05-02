package com.longfeng.common.filter;

import java.time.Clock;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides a singleton {@link Clock} bean for injection into services · TDD §3.1 / plan §5.S0.
 *
 * <p>Defaults to {@link Clock#systemUTC()} in production. Tests can override by declaring their
 * own {@link Clock} bean (e.g. via {@code @TestConfiguration}):
 *
 * <pre>{@code
 *   @TestConfiguration
 *   static class TestConfig {
 *     @Bean
 *     Clock fixedClock() {
 *       return Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
 *     }
 *   }
 * }</pre>
 *
 * <p>Services should declare {@code @Autowired Clock clock} (or constructor-inject) and use
 * {@code clock.instant()} / {@code OffsetDateTime.now(clock)} — never {@code Instant.now()} /
 * {@code LocalDateTime.now()} — to ensure deterministic test behavior (C9 compliance).
 */
@Configuration
public class ClockInjector {

  /** System UTC clock bean, registered only if no other Clock bean is present. */
  @Bean
  @ConditionalOnMissingBean(Clock.class)
  public Clock systemUtcClock() {
    return Clock.systemUTC();
  }

  /**
   * Utility: obtain {@link java.time.OffsetDateTime} at UTC now using the supplied clock.
   * Convenience wrapper so callers don't import OffsetDateTime + ZoneOffset everywhere.
   *
   * @param clock the clock to use (injected, never null)
   * @return current UTC OffsetDateTime
   */
  public static java.time.OffsetDateTime nowUtc(Clock clock) {
    Objects.requireNonNull(clock, "clock must not be null");
    return java.time.OffsetDateTime.now(clock);
  }
}
