package com.longfeng.reviewplan.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * BE-13 · POST /review-plans/batch-reset-by-ids 请求体.
 *
 * <p>spec: {@code { plan_ids: string[] }}（Snowflake long 走 String 防 JS 精度丢失）.
 */
@Schema(description = "按 plan_ids 批量重置请求")
public record BatchResetByIdsReq(
    @Schema(description = "要重置的 plan id 列表 (String Snowflake)")
        @JsonProperty("plan_ids")
        @NotEmpty(message = "plan_ids must not be empty")
        List<String> planIds) {}
