package com.longfeng.reviewplan.service;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.springframework.stereotype.Service;

/**
 * 免打扰（Do-Not-Disturb）服务 · D-DND · S6 BE-10.
 *
 * <p>学生默认 23:00–07:30（本地时区）静音；命中静音时段的节点延迟到次日 08:00（不丢消息）。
 * 使用 {@link ZonedDateTime} DST-aware 计算，依赖 student.preference.timezone。
 *
 * <p>TDD §9.2 参考实现（本仓落地版本 · 与 notification-service 镜像逻辑保持一致）。
 */
@Service
public class DndService {

  /** 默认免打扰开始时间 23:00. */
  private static final LocalTime DEFAULT_DND_START = LocalTime.of(23, 0);

  /** 默认免打扰结束时间 07:30. */
  private static final LocalTime DEFAULT_DND_END = LocalTime.of(7, 30);

  /** 延迟投递时间：次日 08:00. */
  private static final LocalTime DELIVERY_RESUME_TIME = LocalTime.of(8, 0);

  /**
   * 判断给定时刻是否处于免打扰时段.
   *
   * @param when      检测时刻
   * @param timezone  学生时区（student.preference.timezone · 如 "Asia/Shanghai"）
   * @param dndStart  免打扰开始时间（null → DEFAULT_DND_START）
   * @param dndEnd    免打扰结束时间（null → DEFAULT_DND_END）
   * @return true = 处于静音时段
   */
  public boolean isInDnd(Instant when, String timezone, LocalTime dndStart, LocalTime dndEnd) {
    ZoneId zone = ZoneId.of(timezone);
    LocalTime start = dndStart != null ? dndStart : DEFAULT_DND_START;
    LocalTime end   = dndEnd   != null ? dndEnd   : DEFAULT_DND_END;

    LocalTime t = when.atZone(zone).toLocalTime();

    if (start.isBefore(end)) {
      // 非跨午夜（如 09:00–17:00 · 不常见但兼容）
      return !t.isBefore(start) && t.isBefore(end);
    } else {
      // 跨午夜：23:00–07:30 → t >= 23:00 OR t < 07:30
      return !t.isBefore(start) || t.isBefore(end);
    }
  }

  /**
   * 使用默认 DND 窗口（23:00–07:30）判断是否静音.
   */
  public boolean isInDnd(Instant when, String timezone) {
    return isInDnd(when, timezone, DEFAULT_DND_START, DEFAULT_DND_END);
  }

  /**
   * 计算实际投递时间（D-DND：命中静音则顺延到次日 08:00 · 否则保持原时刻）.
   *
   * @param scheduled 原定投递时刻
   * @param timezone  学生时区
   * @return 实际投递时刻（DST-aware · 不早于原定时刻）
   */
  public Instant nextDeliveryTime(Instant scheduled, String timezone) {
    return nextDeliveryTime(scheduled, timezone, DEFAULT_DND_START, DEFAULT_DND_END);
  }

  /**
   * 计算实际投递时间（自定义 DND 窗口版本）.
   */
  public Instant nextDeliveryTime(
      Instant scheduled, String timezone, LocalTime dndStart, LocalTime dndEnd) {
    if (!isInDnd(scheduled, timezone, dndStart, dndEnd)) {
      return scheduled;
    }
    ZoneId zone = ZoneId.of(timezone);
    ZonedDateTime zdt = scheduled.atZone(zone);
    // 顺延到当地次日 08:00 · DST-aware（plusDays(1) 正确处理夏令时切换）
    return zdt.toLocalDate()
        .plusDays(1)
        .atTime(DELIVERY_RESUME_TIME)
        .atZone(zone)
        .toInstant();
  }
}
