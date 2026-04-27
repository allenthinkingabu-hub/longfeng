package com.longfeng.aianalysis.service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * REST view-object for {@code GET /analysis/{itemId}} (G-05 decision · be-build-spec
 * resolved_dtos.AnalysisVO). Field shape matches the OpenAPI contract:
 *
 * <ul>
 *   <li>id / wrong_item_id — Snowflake-Long serialized as string (avoids JS precision loss)
 *   <li>version — int (== PROMPT_VERSION_CODE for that record)
 *   <li>status — string mapping 0→success / 1→fallback / 9→pending (DC drift Q1-R1)
 *   <li>explain — sourced from {@code error_reason} (DD drift)
 *   <li>auto_tags — deserialized from {@code knowledge_points} JSONB array
 *   <li>solution_steps — JSONB pass-through ({@link JsonNode})
 *   <li>finished_at — ISO-8601 string
 * </ul>
 *
 * <p>This DTO carries no business logic — pure transport shape, so the controller layer can
 * serialize it without leaking JPA internals.
 */
@Schema(description = "AI 分析结果 VO · GET /analysis/{itemId}")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AnalysisVO(
    @Schema(description = "Snowflake ID (string to avoid JS precision loss)") @JsonProperty("id") String id,
    @Schema(description = "wrong_item ID (string)") @JsonProperty("wrong_item_id") String wrongItemId,
    @Schema(description = "提示词版本号 PROMPT_VERSION_CODE") @JsonProperty("version") int version,
    @Schema(description = "LLM 提供方 · dashscope | openai | stub") @JsonProperty("model_provider") String modelProvider,
    @Schema(description = "模型名称") @JsonProperty("model_name") String modelName,
    @Schema(description = "分析状态 · success | fallback | pending") @JsonProperty("status") String status,
    @Schema(description = "解题解析 (来自 error_reason 字段)") @JsonProperty("explain") String explain,
    @Schema(description = "错因标签 · CONCEPT | CALCULATION | COMPREHENSION | HANDWRITING | OTHER") @JsonProperty("cause_tag") String causeTag,
    @Schema(description = "知识点标签数组 (来自 knowledge_points)") @JsonProperty("auto_tags") List<String> autoTags,
    @Schema(description = "解题步骤 JSONB 透传") @JsonProperty("solution_steps") JsonNode solutionSteps,
    @Schema(description = "完成时间 ISO-8601") @JsonProperty("finished_at") String finishedAt) {

  public static final String STATUS_SUCCESS = "success";
  public static final String STATUS_FALLBACK = "fallback";
  public static final String STATUS_PENDING = "pending";

  /** Map SMALLINT status (0/1/9) to DTO string value (Q1-R1). */
  public static String mapStatus(Short raw) {
    if (raw == null) {
      return STATUS_PENDING;
    }
    return switch (raw.intValue()) {
      case 0 -> STATUS_SUCCESS;
      case 1 -> STATUS_FALLBACK;
      case 9 -> STATUS_PENDING;
      default -> throw new IllegalStateException("invalid analysis status code: " + raw);
    };
  }
}
