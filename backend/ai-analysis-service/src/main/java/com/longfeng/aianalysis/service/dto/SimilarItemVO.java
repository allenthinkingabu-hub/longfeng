package com.longfeng.aianalysis.service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * REST view-object for {@code GET /analysis/{itemId}/similar} (G-02 decision · be-build-spec
 * resolved_dtos.SimilarItem). Field shape is the contract agreed with the frontend:
 *
 * <ul>
 *   <li>id — Snowflake-Long serialized as string (avoids JS precision loss · BR-17)
 *   <li>stem_text — original wrong_item.stem_text (un-redacted) for direct list rendering
 *   <li>subject — math/physics/chemistry/english/chinese
 *   <li>distance — pgvector cosine distance ∈ [0, 2] · only items with distance ≤ 1.5 returned
 *       (filter executed in service layer to keep ORDER BY index-only · BR-10 / BR-17)
 * </ul>
 */
@Schema(description = "相似题 VO · GET /analysis/{itemId}/similar")
public record SimilarItemVO(
    @Schema(description = "wrong_item ID (string)") @JsonProperty("id") String id,
    @Schema(description = "题干原文") @JsonProperty("stem_text") String stemText,
    @Schema(description = "科目 · math | physics | chemistry | english | chinese") @JsonProperty("subject") String subject,
    @Schema(description = "pgvector cosine distance ∈ [0,2] · ≤1.5 才返回") @JsonProperty("distance") double distance) {}
