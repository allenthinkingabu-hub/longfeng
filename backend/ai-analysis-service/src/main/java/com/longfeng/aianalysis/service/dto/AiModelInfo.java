package com.longfeng.aianalysis.service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * D-AI-Model-Catalog 模型信息 DTO（plan §5.S3 SC-16 + §0.9 D-AI-Model-Catalog）。
 *
 * <p>供 {@code GET /api/ai/models} 返回 · 支持按 X-User-Tier 过滤。
 *
 * <p>字段说明：
 * <ul>
 *   <li>{@code id} — provider:model 唯一标识
 *   <li>{@code provider} — 供应商 (qianwen / openai / zhipu / local-vllm)
 *   <li>{@code displayName} — 用户可读展示名
 *   <li>{@code vipOnly} — true=需要 VIP 或 VIP_PLUS 订阅
 *   <li>{@code supportedSubjects} — 支持学科 (MATH / CHINESE / ENGLISH / PHYSICS / CHEMISTRY / ALL)
 *   <li>{@code costTier} — 调用成本档位 (L=低 / M=中 / H=高)
 *   <li>{@code avgLatencyMs} — 平均延迟 ms（stub 值）
 *   <li>{@code notes} — 备注（供应商特性 / 限制 / 推荐场景）
 * </ul>
 */
@Schema(description = "D-AI-Model-Catalog · AI 模型信息")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AiModelInfo(
    @Schema(description = "provider:model 唯一标识", example = "qianwen:qwen-vl-max")
    @JsonProperty("id") String id,

    @Schema(description = "供应商标识", example = "qianwen")
    @JsonProperty("provider") String provider,

    @Schema(description = "用户可读展示名", example = "通义千问 VL Max")
    @JsonProperty("displayName") String displayName,

    @Schema(description = "是否需要 VIP 或以上订阅", example = "false")
    @JsonProperty("vipOnly") boolean vipOnly,

    @Schema(description = "支持的学科列表", example = "[\"MATH\", \"PHYSICS\"]")
    @JsonProperty("supportedSubjects") List<String> supportedSubjects,

    @Schema(description = "成本档位：L=低 M=中 H=高", example = "M")
    @JsonProperty("costTier") String costTier,

    @Schema(description = "平均延迟 ms（stub 估算）", example = "1200")
    @JsonProperty("avgLatencyMs") int avgLatencyMs,

    @Schema(description = "备注：特性 / 限制 / 推荐场景")
    @JsonProperty("notes") String notes) {}
