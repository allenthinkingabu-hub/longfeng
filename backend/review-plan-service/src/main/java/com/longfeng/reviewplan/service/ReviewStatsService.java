package com.longfeng.reviewplan.service;

import com.longfeng.reviewplan.dto.ReviewStatsResp;
import com.longfeng.reviewplan.dto.ReviewStatsResp.DailyStats;
import com.longfeng.reviewplan.dto.ReviewStatsResp.TopWeakEntry;
import com.longfeng.reviewplan.dto.ReviewStatsResp.Warning;
import com.longfeng.reviewplan.exception.InvalidRangeException;
import com.longfeng.reviewplan.repo.ReviewOutcomeRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * SC-09.AC-1 · GET /review-stats 聚合 · 按 timezone 切日 · Top N 薄弱项.
 *
 * <p>时区策略（Q-E）：按 X-User-Timezone header 解析 ZoneId · 非法 fallback Asia/Shanghai + warnings
 * 标 TIMEZONE_FALLBACK. 180d+ range 返 warnings PARTIAL_HISTORY.
 */
@Service
public class ReviewStatsService {

  private static final Set<String> ALLOWED_RANGES = Set.of("week", "month", "quarter");
  private static final ZoneId DEFAULT_TZ = ZoneId.of("Asia/Shanghai");
  private static final int TOP_N = 3;
  private static final int MIN_SAMPLE = 3;
  private static final int HISTORY_RETENTION_DAYS = 180;

  private final ReviewOutcomeRepository outcomeRepo;

  @Autowired
  public ReviewStatsService(ReviewOutcomeRepository outcomeRepo) {
    this.outcomeRepo = outcomeRepo;
  }

  /**
   * 聚合入口 · 按 range 算时间窗 + timezone 切日.
   *
   * @throws InvalidRangeException if range ∉ {week, month, quarter}
   */
  @Cacheable(
      value = "review-stats",
      key = "#userId + ':' + #range + ':' + (#subject ?: 'all') + ':' + #timezoneHeader")
  public ReviewStatsResp aggregate(
      Long userId, String range, String subject, String timezoneHeader) {
    if (range == null || !ALLOWED_RANGES.contains(range.toLowerCase(Locale.ROOT))) {
      throw new InvalidRangeException(range);
    }

    List<Warning> warnings = new ArrayList<>();

    ZoneId tz = resolveTimezone(timezoneHeader, warnings);
    String normalizedRange = range.toLowerCase(Locale.ROOT);

    Instant endExclusive = Instant.now();
    Instant start = startOf(normalizedRange, endExclusive, tz);

    // PARTIAL_HISTORY: range 跨 180d 归档边界
    long daysSpan = ChronoUnit.DAYS.between(start, endExclusive);
    if (daysSpan > HISTORY_RETENTION_DAYS) {
      warnings.add(
          Warning.partialHistory(
              "range span " + daysSpan + "d > retention " + HISTORY_RETENTION_DAYS + "d"));
    }

    List<DailyStats> daily = queryDaily(userId, subject, tz, start, endExclusive);
    List<TopWeakEntry> topWeak = queryTopWeak(userId, start, endExclusive);

    return new ReviewStatsResp(
        normalizedRange, subject, tz.getId(), daily, topWeak, warnings);
  }

  private ZoneId resolveTimezone(String header, List<Warning> warnings) {
    if (header == null || header.isBlank()) {
      return DEFAULT_TZ;
    }
    try {
      return ZoneId.of(header);
    } catch (Exception e) {
      warnings.add(Warning.timezoneFallback(header));
      return DEFAULT_TZ;
    }
  }

  private Instant startOf(String range, Instant endExclusive, ZoneId tz) {
    LocalDate today = LocalDate.ofInstant(endExclusive, tz);
    return switch (range) {
      case "week" -> today.minusDays(6).atStartOfDay(tz).toInstant(); // 本日 + 前 6 日 = 7 天
      case "month" -> today.minusDays(29).atStartOfDay(tz).toInstant();
      case "quarter" -> today.minusDays(89).atStartOfDay(tz).toInstant();
      default -> throw new InvalidRangeException(range);
    };
  }

  private List<DailyStats> queryDaily(
      Long userId, String subject, ZoneId tz, Instant start, Instant endExclusive) {
    List<Object[]> rows =
        outcomeRepo.aggregateDailyStats(userId, start, endExclusive, subject, tz.getId());

    // 按 range 填满每一天（没复习的 day 补 null correctRate / 0 counts）
    Map<LocalDate, DailyStats> byDay = new HashMap<>();
    for (Object[] r : rows) {
      LocalDate d = ((Date) r[0]).toLocalDate();
      BigDecimal rate =
          r[1] == null ? null : ((BigDecimal) r[1]).setScale(4, RoundingMode.HALF_UP);
      int mastered = r[2] == null ? 0 : ((Number) r[2]).intValue();
      int reviews = r[3] == null ? 0 : ((Number) r[3]).intValue();
      byDay.put(d, new DailyStats(d, rate, mastered, reviews));
    }

    LocalDate startDay = LocalDate.ofInstant(start, tz);
    LocalDate todayExcl = LocalDate.ofInstant(endExclusive, tz); // 本日未完
    List<DailyStats> out = new ArrayList<>();
    for (LocalDate d = startDay; !d.isAfter(todayExcl); d = d.plusDays(1)) {
      out.add(byDay.getOrDefault(d, new DailyStats(d, null, 0, 0)));
    }
    return out;
  }

  private List<TopWeakEntry> queryTopWeak(Long userId, Instant start, Instant endExclusive) {
    List<Object[]> rows =
        outcomeRepo.topWeakSubjects(userId, start, endExclusive, MIN_SAMPLE, TOP_N);
    List<TopWeakEntry> out = new ArrayList<>(rows.size());
    for (Object[] r : rows) {
      String subject = (String) r[0];
      int total = ((Number) r[1]).intValue();
      int forget = ((Number) r[2]).intValue();
      BigDecimal rate =
          r[3] == null ? BigDecimal.ZERO : ((BigDecimal) r[3]).setScale(4, RoundingMode.HALF_UP);
      out.add(new TopWeakEntry(subject, total, forget, rate));
    }
    return out;
  }

  /** 供 IT 直接注入 · 不用 Spring cache 走 Service. */
  public static final Set<String> allowedRanges() {
    return ALLOWED_RANGES;
  }

  /** 文档锚点：使用的常量一览（便于 arch grep）. */
  @SuppressWarnings("unused")
  public static final String[] DOC_ANCHORS = {
      "review-stats", "Asia/Shanghai", "PARTIAL_HISTORY", "TIMEZONE_FALLBACK",
      Arrays.toString(new Object[]{TOP_N, MIN_SAMPLE, HISTORY_RETENTION_DAYS})
  };
}
