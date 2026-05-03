package com.longfeng.reviewplan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

import com.longfeng.common.test.CoversAC;
import com.longfeng.reviewplan.dto.ReviewStatsResp;
import com.longfeng.reviewplan.dto.ReviewStatsResp.DailyStats;
import com.longfeng.reviewplan.dto.ReviewStatsResp.TopWeakEntry;
import com.longfeng.reviewplan.dto.ReviewStatsResp.Warning;
import com.longfeng.reviewplan.exception.InvalidRangeException;
import com.longfeng.reviewplan.repo.ReviewOutcomeRepository;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * ReviewStatsServiceTest · 单元测试 · SC-09.AC-1 各路径覆盖.
 *
 * <p>Strategy：mock ReviewOutcomeRepository · 不依赖 DB · 快速覆盖时区/range/warning/aggregate 逻辑。
 *
 * <p>@MockitoSettings(LENIENT) · 避免 setUp 共用 stub 报 "unnecessary stubbing"。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReviewStatsServiceTest {

  @Mock
  private ReviewOutcomeRepository outcomeRepo;

  private ReviewStatsService statsService;

  private static final Long USER_ID = 9900001L;

  @BeforeEach
  void setUp() {
    statsService = new ReviewStatsService(outcomeRepo);

    // 默认 stub: 两个聚合查询返回空 · 具体测试用例再 override
    when(outcomeRepo.aggregateDailyStats(
            any(), any(), any(), any(), anyString()))
        .thenReturn(Collections.emptyList());
    when(outcomeRepo.topWeakSubjects(
            any(), any(), any(), anyInt(), anyInt()))
        .thenReturn(Collections.emptyList());
  }

  // ======================================================================
  // R-T1 · range=week → 7 day 数组（按 Asia/Shanghai 切日）
  // ======================================================================

  @Test
  @DisplayName("R-T1 · range=week · 空数据 → 7 DailyStats 全 null correctRate")
  @CoversAC("SC-09.AC-1#stats_week_empty.0")
  void stats_week_returns_seven_days_all_null() {
    ReviewStatsResp resp = statsService.aggregate(USER_ID, "week", null, "Asia/Shanghai");

    assertThat(resp.range()).isEqualTo("week");
    assertThat(resp.timezone()).isEqualTo("Asia/Shanghai");
    assertThat(resp.data()).hasSize(7);
    assertThat(resp.data()).allSatisfy(d -> {
      assertThat(d.correctRate()).isNull();
      assertThat(d.reviewCount()).isEqualTo(0);
      assertThat(d.masteredCount()).isEqualTo(0);
    });
    assertThat(resp.topWeak()).isEmpty();
    assertThat(resp.warnings()).isEmpty();
  }

  // ======================================================================
  // R-T2 · range=month → 30 day 数组
  // ======================================================================

  @Test
  @DisplayName("R-T2 · range=month · 空数据 → 30 DailyStats")
  @CoversAC("SC-09.AC-1#stats_month_empty.0")
  void stats_month_returns_thirty_days() {
    ReviewStatsResp resp = statsService.aggregate(USER_ID, "month", null, "Asia/Shanghai");

    assertThat(resp.range()).isEqualTo("month");
    assertThat(resp.data()).hasSize(30);
  }

  // ======================================================================
  // R-T3 · range=quarter → 90 day 数组
  // ======================================================================

  @Test
  @DisplayName("R-T3 · range=quarter · 空数据 → 90 DailyStats")
  @CoversAC("SC-09.AC-1#stats_quarter_empty.0")
  void stats_quarter_returns_ninety_days() {
    ReviewStatsResp resp = statsService.aggregate(USER_ID, "quarter", null, "Asia/Shanghai");

    assertThat(resp.data()).hasSize(90);
  }

  // ======================================================================
  // R-T4 · range=year → InvalidRangeException (400)
  // ======================================================================

  @Test
  @DisplayName("R-T4 · range=year → InvalidRangeException · errCode INVALID_RANGE")
  @CoversAC("SC-09.AC-1#invalid_range.0")
  void stats_invalid_range_throws() {
    assertThatThrownBy(() -> statsService.aggregate(USER_ID, "year", null, null))
        .isInstanceOf(InvalidRangeException.class)
        .hasMessageContaining("year");
  }

  // ======================================================================
  // R-T5 · range=null → InvalidRangeException
  // ======================================================================

  @Test
  @DisplayName("R-T5 · range=null → InvalidRangeException")
  @CoversAC("SC-09.AC-1#invalid_range.1")
  void stats_null_range_throws() {
    assertThatThrownBy(() -> statsService.aggregate(USER_ID, null, null, null))
        .isInstanceOf(InvalidRangeException.class);
  }

  // ======================================================================
  // R-T6 · 无效 timezone → TIMEZONE_FALLBACK warning + 降级 Asia/Shanghai
  // ======================================================================

  @Test
  @DisplayName("R-T6 · 无效 timezone 'Bad/Zone' → TIMEZONE_FALLBACK warning + 降级 Asia/Shanghai")
  @CoversAC("SC-09.AC-1#timezone_fallback.0")
  void stats_invalid_timezone_adds_fallback_warning() {
    ReviewStatsResp resp = statsService.aggregate(USER_ID, "week", null, "Bad/Zone");

    assertThat(resp.timezone()).isEqualTo("Asia/Shanghai");
    assertThat(resp.warnings())
        .anySatisfy(w -> assertThat(w.code()).isEqualTo("TIMEZONE_FALLBACK"));
  }

  // ======================================================================
  // R-T7 · timezone=null → 默认 Asia/Shanghai · 无 warning
  // ======================================================================

  @Test
  @DisplayName("R-T7 · timezone=null → 默认 Asia/Shanghai · 无 warning")
  @CoversAC("SC-09.AC-1#timezone_default.0")
  void stats_null_timezone_uses_default() {
    ReviewStatsResp resp = statsService.aggregate(USER_ID, "week", null, null);

    assertThat(resp.timezone()).isEqualTo("Asia/Shanghai");
    assertThat(resp.warnings())
        .noneSatisfy(w -> assertThat(w.code()).isEqualTo("TIMEZONE_FALLBACK"));
  }

  // ======================================================================
  // R-T8 · 有 review 数据 → correctRate / masteredCount 正确映射
  // ======================================================================

  @Test
  @DisplayName("R-T8 · repo 返回 1 天数据 → correctRate + masteredCount 正确映射")
  @CoversAC("SC-09.AC-1#stats_data_mapping.0")
  void stats_with_data_maps_daily_stats_correctly() {
    LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
    java.sql.Date sqlDate = Date.valueOf(today);
    // Object[]: [0]=date [1]=correctRate [2]=masteredCount [3]=reviewCount
    Object[] row = new Object[]{sqlDate, new BigDecimal("0.7500"), 2L, 10L};
    when(outcomeRepo.aggregateDailyStats(
            eq(USER_ID), any(Instant.class), any(Instant.class),
            isNull(), eq("Asia/Shanghai")))
        .thenReturn(java.util.Collections.<Object[]>singletonList(row));

    ReviewStatsResp resp = statsService.aggregate(USER_ID, "week", null, "Asia/Shanghai");

    assertThat(resp.data()).hasSize(7);
    DailyStats todayStats = resp.data().stream()
        .filter(d -> d.date().equals(today))
        .findFirst()
        .orElseThrow(() -> new AssertionError("today stats not found"));

    assertThat(todayStats.correctRate()).isEqualByComparingTo("0.7500");
    assertThat(todayStats.masteredCount()).isEqualTo(2);
    assertThat(todayStats.reviewCount()).isEqualTo(10);
  }

  // ======================================================================
  // R-T9 · topWeak 正确映射（subject / totalReview / forgetCount / forgetRate）
  // ======================================================================

  @Test
  @DisplayName("R-T9 · repo 返回 topWeak → subject/totalReview/forgetCount/forgetRate 正确")
  @CoversAC("SC-09.AC-1#top_weak_mapping.0")
  void stats_top_weak_entries_mapped_correctly() {
    Object[] row = new Object[]{"math", 20L, 15L, new BigDecimal("0.7500")};
    when(outcomeRepo.topWeakSubjects(
            eq(USER_ID), any(Instant.class), any(Instant.class), anyInt(), anyInt()))
        .thenReturn(java.util.Collections.<Object[]>singletonList(row));

    ReviewStatsResp resp = statsService.aggregate(USER_ID, "week", null, "Asia/Shanghai");

    assertThat(resp.topWeak()).hasSize(1);
    TopWeakEntry entry = resp.topWeak().get(0);
    assertThat(entry.subject()).isEqualTo("math");
    assertThat(entry.totalReview()).isEqualTo(20);
    assertThat(entry.forgetCount()).isEqualTo(15);
    assertThat(entry.forgetRate()).isEqualByComparingTo("0.7500");
  }

  // ======================================================================
  // R-T10 · allowedRanges() 静态常量包含 week/month/quarter
  // ======================================================================

  @Test
  @DisplayName("R-T10 · allowedRanges() 包含 week/month/quarter 三档")
  void allowed_ranges_contains_three_values() {
    var ranges = ReviewStatsService.allowedRanges();
    assertThat(ranges).containsExactlyInAnyOrder("week", "month", "quarter");
  }

  // ======================================================================
  // R-T11 · range 大小写不敏感 · "WEEK" 等价于 "week"
  // ======================================================================

  @Test
  @DisplayName("R-T11 · range 大小写不敏感 · 'WEEK' 解析为 week")
  @CoversAC("SC-09.AC-1#range_case_insensitive.0")
  void stats_range_case_insensitive() {
    ReviewStatsResp resp = statsService.aggregate(USER_ID, "WEEK", null, "Asia/Shanghai");

    assertThat(resp.range()).isEqualTo("week");
    assertThat(resp.data()).hasSize(7);
  }

  // ======================================================================
  // R-T12 · subject 过滤不影响返回结构（仅 repo 层 filter）
  // ======================================================================

  @Test
  @DisplayName("R-T12 · subject=math 参数透传到 repo · 返回 7 天结构不变")
  @CoversAC("SC-09.AC-1#stats_subject_filter.0")
  void stats_with_subject_filter_returns_correct_structure() {
    ReviewStatsResp resp = statsService.aggregate(USER_ID, "week", "math", "Asia/Shanghai");

    assertThat(resp.subject()).isEqualTo("math");
    assertThat(resp.data()).hasSize(7);
  }
}
