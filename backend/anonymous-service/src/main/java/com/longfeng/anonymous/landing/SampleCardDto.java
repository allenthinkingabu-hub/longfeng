package com.longfeng.anonymous.landing;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * SampleCard DTO returned from {@code GET /api/landing/samples}.
 *
 * <p>Field names are camelCase to match the FE schema in
 * {@code frontend/apps/h5/src/__mocks__/handlers/guest.ts}. Optional fields are omitted when null.
 *
 * @param id              stable card id (e.g. {@code sample-1})
 * @param subject         {@code math} | {@code physics} | {@code chemistry} | {@code english}
 * @param stemPreview     short stem preview (max ~80 chars)
 * @param thumbnailUrl    optional thumbnail URL
 * @param formula         optional formula string (LaTeX-ish or plain)
 * @param errorReason     short error-reason label
 * @param kpLabel         short knowledge-point label
 * @param tagLabel        short Ebbinghaus tag label
 * @param aiAnalysisMock  optional AI analysis preview block
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SampleCardDto(
    String id,
    String subject,
    String stemPreview,
    String thumbnailUrl,
    String formula,
    String errorReason,
    String kpLabel,
    String tagLabel,
    AiAnalysisMockDto aiAnalysisMock) {

  /** Inline AI analysis preview block. */
  public record AiAnalysisMockDto(String reason, int stepsCount, String hint) {}
}
