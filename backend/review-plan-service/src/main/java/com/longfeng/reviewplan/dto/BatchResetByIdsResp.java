package com.longfeng.reviewplan.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/** BE-13 · POST /review-plans/batch-reset-by-ids 响应体 · resp count. */
@Schema(description = "按 plan_ids 批量重置响应")
public record BatchResetByIdsResp(
    @Schema(description = "实际重置成功的节点数（不存在/已 mastered/已 soft-delete 不计）")
        @JsonProperty("reset_count")
        int resetCount) {}
