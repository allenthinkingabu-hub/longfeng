package com.longfeng.aianalysis.service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
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
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AnalysisVO(
    @JsonProperty("id") String id,
    @JsonProperty("wrong_item_id") String wrongItemId,
    @JsonProperty("version") int version,
    @JsonProperty("model_provider") String modelProvider,
    @JsonProperty("model_name") String modelName,
    @JsonProperty("status") String status,
    @JsonProperty("explain") String explain,
    @JsonProperty("cause_tag") String causeTag,
    @JsonProperty("auto_tags") List<String> autoTags,
    @JsonProperty("solution_steps") JsonNode solutionSteps,
    @JsonProperty("finished_at") String finishedAt) {

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
