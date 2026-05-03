package com.longfeng.reviewplan.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * BE-13 · GET /review-plans/list 响应（cursor 翻页）.
 *
 * <p>排序 created_at DESC + id DESC · cursor stable. {@code next_cursor=null} 表示已到末页.
 */
@Schema(description = "review-plan 列表响应（cursor 翻页）")
public record ListReviewPlanResp(
    @Schema(description = "本页节点列表") @JsonProperty("items") List<ReviewPlanDto> items,
    @Schema(description = "下一页 cursor（null = 末页）·当前实现使用 numeric id")
        @JsonProperty("next_cursor")
        String nextCursor) {}
