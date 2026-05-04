package com.longfeng.wrongbook.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * P04 Result page DTO · plannedNodes preview entry · 1:1 aligned with FE
 * {@code interface PlannedNode} in Result/index.tsx.
 */
@Schema(description = "艾宾浩斯节点预览 · GET /api/wb/questions/{qid}.plannedNodes")
public record PlannedNodeDto(
    @Schema(description = "节点等级 T1..T6") @JsonProperty("t_level") String tLevel,
    @Schema(description = "ISO timestamp · 到期时间") @JsonProperty("due_at") String dueAt,
    @Schema(description = "preview / future / done · 预览总是 preview") String status) {}
