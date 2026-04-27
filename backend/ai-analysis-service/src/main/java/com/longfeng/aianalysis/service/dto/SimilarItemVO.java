package com.longfeng.aianalysis.service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

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
public record SimilarItemVO(
    @JsonProperty("id") String id,
    @JsonProperty("stem_text") String stemText,
    @JsonProperty("subject") String subject,
    @JsonProperty("distance") double distance) {}
