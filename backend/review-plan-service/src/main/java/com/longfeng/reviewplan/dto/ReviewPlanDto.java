package com.longfeng.reviewplan.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.longfeng.reviewplan.entity.ReviewPlan;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/** GET /review-plans/{id} + GET /review-plans 日视图 response VO · contract gap G-01~G-06 resolved. */
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
    @Schema(description = "连续好评次数 0..3（掌握进度）")
        @JsonProperty("mastery")
        int mastery,
    @Schema(description = "当前 ease factor (SM-2)")
        @JsonProperty("ease_factor")
        BigDecimal easeFactor,
    @Schema(description = "当前节点间隔索引 (0..6)")
        @JsonProperty("interval")
        int interval,
    @Schema(description = "状态: active | mastered")
        @JsonProperty("status")
        String status) {

  public static ReviewPlanDto from(ReviewPlan plan) {
    return new ReviewPlanDto(
        String.valueOf(plan.getId()),
        String.valueOf(plan.getWrongItemId()),
        String.valueOf(plan.getStudentId()),
        plan.getNodeIndex() != null ? plan.getNodeIndex() : 0,
        plan.getNextDueAt() != null ? plan.getNextDueAt().toString() : null,
        plan.getConsecutiveGoodCount() != null ? plan.getConsecutiveGoodCount() : 0,
        plan.getEaseFactor(),
        plan.getIntervalIndex() != null ? plan.getIntervalIndex() : 0,
        plan.getStatus() != null && plan.getStatus() == ReviewPlan.STATUS_MASTERED
            ? "mastered"
            : "active");
  }
}
