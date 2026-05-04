package com.longfeng.wrongbook.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Feign client for ai-analysis-service · {@code GET /analysis/{itemId}} · returns the latest
 * AnalysisVO (mirrors {@code com.longfeng.aianalysis.service.dto.AnalysisVO}).
 *
 * <p>Distinct from {@link AiAnalysisClient} which only exposes the embedding endpoint. The
 * {@link AnalysisDetailClientFallback} returns {@code null} so the caller can fall back to mock
 * data without throwing.
 */
@FeignClient(
    name = "ai-analysis-detail",
    url = "${feign.client.ai-analysis.url:http://localhost:8083}",
    fallback = AnalysisDetailClientFallback.class)
public interface AnalysisDetailClient {

  @GetMapping("/analysis/{itemId}")
  AnalysisDetailResponse latest(@PathVariable("itemId") Long itemId);

  /** Subset of ai-analysis AnalysisVO required by P04. */
  record AnalysisDetailResponse(
      String id,
      @JsonProperty("wrong_item_id") String wrongItemId,
      int version,
      @JsonProperty("model_provider") String modelProvider,
      @JsonProperty("model_name") String modelName,
      String status,
      String explain,
      @JsonProperty("cause_tag") String causeTag,
      @JsonProperty("auto_tags") List<String> autoTags,
      @JsonProperty("solution_steps") Object solutionSteps,
      @JsonProperty("finished_at") String finishedAt) {}
}
