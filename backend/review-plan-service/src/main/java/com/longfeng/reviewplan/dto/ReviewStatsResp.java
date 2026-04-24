package com.longfeng.reviewplan.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** GET /review-stats response · SC-09.AC-1 · 日视图趋势 + Top N 薄弱项. */
public record ReviewStatsResp(
    String range,                         // week | month | quarter
    String subject,                       // 可为 null（全科目）
    String timezone,                      // 实际使用的 timezone（可能降级到 Asia/Shanghai）
    List<DailyStats> data,                // 按日切片
    List<TopWeakEntry> topWeak,           // Top N 薄弱项（按遗忘率倒序 · TopN 默认 3）
    List<Warning> warnings) {

  /** 单日聚合行（按用户 timezone 切日）. */
  public record DailyStats(
      LocalDate date,
      BigDecimal correctRate,             // 可为 null（该日 0 次复习）
      Integer masteredCount,              // 该日内触发 mastered 的错题数
      Integer reviewCount) {}             // 该日 complete 总次数

  /** Top N 薄弱项（subject 级别 · 按 forget_rate 倒序）. */
  public record TopWeakEntry(
      String subject,
      Integer totalReview,
      Integer forgetCount,                // quality<3 的次数
      BigDecimal forgetRate) {}           // forget / total

  /** 响应警告（非错误 · 如 PARTIAL_HISTORY · 跨 180d 归档边界）. */
  public record Warning(String code, String detail) {
    public static Warning partialHistory(String detail) {
      return new Warning("PARTIAL_HISTORY", detail);
    }

    public static Warning timezoneFallback(String requested) {
      return new Warning(
          "TIMEZONE_FALLBACK", "requested=" + requested + " · 降级默认 Asia/Shanghai");
    }
  }
}
