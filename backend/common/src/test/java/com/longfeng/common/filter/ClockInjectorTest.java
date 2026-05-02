package com.longfeng.common.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ClockInjector} · TDD §3.1 / plan §5.S0 BE-01.
 *
 * <p>Covers:
 * <ul>
 *   <li>Default clock is UTC</li>
 *   <li>nowUtc() uses the provided clock</li>
 *   <li>Fixed clock for deterministic tests</li>
 *   <li>Null clock throws NullPointerException</li>
 * </ul>
 */
class ClockInjectorTest {

  @Test
  void systemUtcClock_isUtc() {
    ClockInjector injector = new ClockInjector();
    Clock clock = injector.systemUtcClock();

    assertThat(clock.getZone()).isEqualTo(ZoneOffset.UTC);
  }

  @Test
  void nowUtc_withFixedClock_returnsExpectedTime() {
    Instant fixedInstant = Instant.parse("2026-01-15T08:00:00Z");
    Clock fixedClock = Clock.fixed(fixedInstant, ZoneOffset.UTC);

    OffsetDateTime result = ClockInjector.nowUtc(fixedClock);

    assertThat(result.getYear()).isEqualTo(2026);
    assertThat(result.getMonthValue()).isEqualTo(1);
    assertThat(result.getDayOfMonth()).isEqualTo(15);
    assertThat(result.getHour()).isEqualTo(8);
    assertThat(result.getOffset()).isEqualTo(ZoneOffset.UTC);
  }

  @Test
  void nowUtc_withNullClock_throwsNullPointer() {
    assertThatThrownBy(() -> ClockInjector.nowUtc(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("clock");
  }

  @Test
  void nowUtc_returnsOffsetDateTimeNotLocalDateTime() {
    Clock clock = Clock.systemUTC();

    OffsetDateTime result = ClockInjector.nowUtc(clock);

    // Must be OffsetDateTime (C9 compliance)
    assertThat(result).isInstanceOf(OffsetDateTime.class);
    assertThat(result.getOffset()).isEqualTo(ZoneOffset.UTC);
  }
}
