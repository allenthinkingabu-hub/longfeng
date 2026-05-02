package com.longfeng.reviewplan.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;

/** GET /review-plans?date= 日视图 response · SC-07 支撑 + SC-10 calendar 节点. */
@Schema(description = "日视图 response")
public record DayViewResp(
    @Schema(description = "当日 due 复习节点列表") List<ReviewPlanDto> items,
    @Schema(description = "日历平台节点（SC-10 · 来自 core-service）")
        List<Map<String, Object>> calendarNodes,
    @Schema(description = "数据来源: fresh | cache:Nm | unavailable")
        String source) {}
