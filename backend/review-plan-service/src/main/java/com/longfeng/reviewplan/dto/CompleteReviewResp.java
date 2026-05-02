package com.longfeng.reviewplan.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/** POST /review-plans/{id}/complete response · SC-08.AC-1 · snake_case contract. */
@Schema(description = "complete 响应 VO")
public record CompleteReviewResp(
    @Schema(description = "计划节点 ID (String Snowflake)") @JsonProperty("plan_id") String planId,
    @Schema(description = "下次到期时间 ISO-8601 UTC") @JsonProperty("next_review_at")
        String nextReviewAt,
    @Schema(description = "更新后 ease factor") @JsonProperty("ease_factor_after")
        BigDecimal easeFactorAfter,
    @Schema(description = "是否本次触发 mastered") boolean mastered) {}
