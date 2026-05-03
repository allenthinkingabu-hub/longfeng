package com.longfeng.reviewplan.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.longfeng.reviewplan.entity.ReviewPlan;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Duration;

/**
 * GET /review-plans/{id} + GET /review-plans 日视图 response VO · contract gap G-01~G-06 resolved.
 *
 * <p>BE-13 (S5 caveat C-26-BE) · 4 字段语义补齐：
 * <ul>
 *   <li>{@code user_id} (existing) — 学生 ID
 *   <li>{@code next_due_at} (existing) — 下次复习节点 due time (ISO-8601 UTC)
 *   <li>{@code mastery} (existing int=consecutive_good_count) + {@code mastery_label}
 *       (NEW/LEARNING/MASTERED) — 枚举形态（兼容 BE-13 spec）
 *   <li>{@code interval} (existing int=node index 0..6) + {@code interval_days}
 *       (1/3/6/15/30 天数) — 天数形态（兼容 BE-13 spec）
 * </ul>
 *
 * <p>保留旧字段是为了不打挂 47/47 IT · 新增 mastery_label / interval_days 是为了对齐 contract spec.
 */
@Schema(description = "复习计划节点 VO")
public record ReviewPlanDto(
    @Schema(description = "节点 ID (String Snowflake)")
        @JsonProperty("id")
        String id,
    @Schema(description = "错题 ID (String Snowflake)")
        @JsonProperty("wrong_item_id")
        String wrongItemId,
    @Schema(description = "学生 ID (String Snowflake)")
        @JsonProperty("user_id")
        String userId,
    @Schema(description = "艾宾浩斯节点编号 0..6")
        @JsonProperty("node_index")
        int nodeIndex,
    @Schema(description = "到期时间 ISO-8601 UTC")
        @JsonProperty("next_due_at")
        String nextDueAt,
    @Schema(description = "连续好评次数 0..3（掌握进度数值）")
        @JsonProperty("mastery")
        int mastery,
    @Schema(description = "掌握状态枚举: NEW | LEARNING | MASTERED (BE-13 spec)")
        @JsonProperty("mastery_label")
        String masteryLabel,
    @Schema(description = "当前 ease factor (SM-2)")
        @JsonProperty("ease_factor")
        BigDecimal easeFactor,
    @Schema(description = "当前节点间隔索引 (0..6)")
        @JsonProperty("interval")
        int interval,
    @Schema(description = "当前节点间隔天数 (T1=1 / T2=2 / T3=4 / T4=7 / T5=14 / T6=30 · BE-13 spec)")
        @JsonProperty("interval_days")
        int intervalDays,
    @Schema(description = "状态: active | mastered")
        @JsonProperty("status")
        String status) {

  /** 7 节点偏移天数（与 ReviewPlanService.NODE_OFFSETS 对齐 · T0 是 2h 故记 0 天）. */
  private static final int[] INTERVAL_DAYS_BY_NODE = {0, 1, 2, 4, 7, 14, 30};

  public static ReviewPlanDto from(ReviewPlan plan) {
    int nodeIdx = plan.getNodeIndex() != null ? plan.getNodeIndex() : 0;
    int intervalIdx = plan.getIntervalIndex() != null ? plan.getIntervalIndex() : 0;
    int consecutiveGood =
        plan.getConsecutiveGoodCount() != null ? plan.getConsecutiveGoodCount() : 0;
    boolean mastered =
        plan.getStatus() != null && plan.getStatus() == ReviewPlan.STATUS_MASTERED;
    String masteryLabel;
    if (mastered) {
      masteryLabel = "MASTERED";
    } else if (consecutiveGood == 0 && (plan.getTotalReview() == null || plan.getTotalReview() == 0)) {
      masteryLabel = "NEW";
    } else {
      masteryLabel = "LEARNING";
    }
    int intervalDays = computeIntervalDays(plan, intervalIdx);
    return new ReviewPlanDto(
        String.valueOf(plan.getId()),
        String.valueOf(plan.getWrongItemId()),
        String.valueOf(plan.getStudentId()),
        nodeIdx,
        plan.getNextDueAt() != null ? plan.getNextDueAt().toString() : null,
        consecutiveGood,
        masteryLabel,
        plan.getEaseFactor(),
        intervalIdx,
        intervalDays,
        mastered ? "mastered" : "active");
  }

  /** 优先用 next_due_at - start_at 实际差值 · 不可用则按 node 表回退. */
  private static int computeIntervalDays(ReviewPlan plan, int intervalIdx) {
    if (plan.getNextDueAt() != null && plan.getStartAt() != null) {
      long days = Duration.between(plan.getStartAt(), plan.getNextDueAt()).toDays();
      if (days > 0) return (int) days;
    }
    int safeIdx = Math.max(0, Math.min(INTERVAL_DAYS_BY_NODE.length - 1, intervalIdx));
    return INTERVAL_DAYS_BY_NODE[safeIdx];
  }
}
