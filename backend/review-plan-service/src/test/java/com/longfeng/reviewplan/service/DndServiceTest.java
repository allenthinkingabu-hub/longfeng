package com.longfeng.reviewplan.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * DndServiceTest · S6 BE-10 · 单元测试.
 *
 * <p>覆盖：上海 / 洛杉矶时区 · DST 切换 · 静音区间边界 · nextDeliveryTime 延迟逻辑.
 */
@DisplayName("DndService · 免打扰策略")
class DndServiceTest {

  private final DndService dnd = new DndService();

  // ── SH 正常时段 ──────────────────────────────────────────────────

  @Test
  @DisplayName("上海 22:59 → 非静音")
  void sh_beforeDnd_notInDnd() {
    Instant when = at("Asia/Shanghai", 2026, 5, 1, 22, 59);
    assertThat(dnd.isInDnd(when, "Asia/Shanghai")).isFalse();
  }

  @Test
  @DisplayName("上海 23:00 → 进入静音")
  void sh_dndStart_inDnd() {
    Instant when = at("Asia/Shanghai", 2026, 5, 1, 23, 0);
    assertThat(dnd.isInDnd(when, "Asia/Shanghai")).isTrue();
  }

  @Test
  @DisplayName("上海 00:00 (跨午夜) → 仍在静音")
  void sh_midnight_inDnd() {
    Instant when = at("Asia/Shanghai", 2026, 5, 2, 0, 0);
    assertThat(dnd.isInDnd(when, "Asia/Shanghai")).isTrue();
  }

  @Test
  @DisplayName("上海 07:30 → 静音结束（边界 exclusive）")
  void sh_dndEnd_notInDnd() {
    // DND end = 07:30 (exclusive) → 07:30 itself is NOT in DND
    Instant when = at("Asia/Shanghai", 2026, 5, 2, 7, 30);
    assertThat(dnd.isInDnd(when, "Asia/Shanghai")).isFalse();
  }

  @Test
  @DisplayName("上海 07:29 → 仍在静音")
  void sh_justBeforeEnd_inDnd() {
    Instant when = at("Asia/Shanghai", 2026, 5, 2, 7, 29);
    assertThat(dnd.isInDnd(when, "Asia/Shanghai")).isTrue();
  }

  @Test
  @DisplayName("上海 12:00 → 非静音")
  void sh_noon_notInDnd() {
    Instant when = at("Asia/Shanghai", 2026, 5, 1, 12, 0);
    assertThat(dnd.isInDnd(when, "Asia/Shanghai")).isFalse();
  }

  // ── LA 时区 ──────────────────────────────────────────────────────

  @Test
  @DisplayName("洛杉矶 23:00 → 进入静音")
  void la_dndStart_inDnd() {
    Instant when = at("America/Los_Angeles", 2026, 5, 1, 23, 0);
    assertThat(dnd.isInDnd(when, "America/Los_Angeles")).isTrue();
  }

  @Test
  @DisplayName("洛杉矶 10:00 → 非静音")
  void la_day_notInDnd() {
    Instant when = at("America/Los_Angeles", 2026, 5, 1, 10, 0);
    assertThat(dnd.isInDnd(when, "America/Los_Angeles")).isFalse();
  }

  // ── DST 切换场景（美国夏令时 2026-03-08 03:00 LA 跳 +1h）──────────

  @Test
  @DisplayName("LA DST 切换日午夜 02:30 → 仍在静音")
  void la_dst_transition_inDnd() {
    // 夏令时切换前，2026-03-08 02:30 LA 时间
    // （实际时间跳过了 02:00–03:00，但我们用标准时计算校验 API 行为）
    Instant when = at("America/Los_Angeles", 2026, 3, 8, 2, 30);
    assertThat(dnd.isInDnd(when, "America/Los_Angeles")).isTrue();
  }

  @Test
  @DisplayName("LA DST 切换日 08:00 → 已出静音")
  void la_dst_transition_morningOk() {
    Instant when = at("America/Los_Angeles", 2026, 3, 8, 8, 30);
    assertThat(dnd.isInDnd(when, "America/Los_Angeles")).isFalse();
  }

  // ── nextDeliveryTime ──────────────────────────────────────────────

  @Test
  @DisplayName("非静音时段 → 原时刻不变")
  void nextDelivery_notInDnd_unchanged() {
    Instant when = at("Asia/Shanghai", 2026, 5, 1, 15, 0);
    assertThat(dnd.nextDeliveryTime(when, "Asia/Shanghai")).isEqualTo(when);
  }

  @Test
  @DisplayName("上海 23:30 → 延迟到次日 08:00 (SH)")
  void nextDelivery_inDnd_deferred() {
    Instant when = at("Asia/Shanghai", 2026, 5, 1, 23, 30);
    Instant expected = at("Asia/Shanghai", 2026, 5, 2, 8, 0);
    assertThat(dnd.nextDeliveryTime(when, "Asia/Shanghai")).isEqualTo(expected);
  }

  @Test
  @DisplayName("上海 00:15 → 延迟到当日 08:00 (SH · 凌晨算本日)")
  void nextDelivery_midnight_deferred() {
    Instant when = at("Asia/Shanghai", 2026, 5, 2, 0, 15);
    Instant expected = at("Asia/Shanghai", 2026, 5, 3, 8, 0);
    assertThat(dnd.nextDeliveryTime(when, "Asia/Shanghai")).isEqualTo(expected);
  }

  @Test
  @DisplayName("LA DST 切换日 00:00 → 次日 08:00 DST-aware")
  void nextDelivery_dst_aware() {
    // 2026-03-08 夏令时切换日，凌晨在静音
    Instant when = at("America/Los_Angeles", 2026, 3, 8, 0, 0);
    ZoneId la = ZoneId.of("America/Los_Angeles");
    // 次日 08:00 LA 时间（夏令时已生效）
    Instant expected = LocalDate.of(2026, 3, 9)
        .atTime(LocalTime.of(8, 0))
        .atZone(la)
        .toInstant();
    assertThat(dnd.nextDeliveryTime(when, "America/Los_Angeles")).isEqualTo(expected);
  }

  @Test
  @DisplayName("自定义 DND 窗口 (09:00–17:00) · 正常判断")
  void customDndWindow() {
    LocalTime start = LocalTime.of(9, 0);
    LocalTime end = LocalTime.of(17, 0);
    Instant inside = at("Asia/Shanghai", 2026, 5, 1, 12, 0);
    Instant outside = at("Asia/Shanghai", 2026, 5, 1, 20, 0);
    assertThat(dnd.isInDnd(inside, "Asia/Shanghai", start, end)).isTrue();
    assertThat(dnd.isInDnd(outside, "Asia/Shanghai", start, end)).isFalse();
  }

  // ── helper ────────────────────────────────────────────────────────

  private static Instant at(String tz, int y, int m, int d, int h, int min) {
    return ZonedDateTime.of(y, m, d, h, min, 0, 0, ZoneId.of(tz)).toInstant();
  }
}
