package com.longfeng.wrongbook.client;

import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Feign stub for ai-analysis-service embedding endpoint.
 *
 * <p>C10: Cross-service call via OpenFeign with Sentinel fallback.
 * NOTE: ai-analysis-service is not yet deployed in Phase S4 (caveat C-14).
 * The {@link AiAnalysisClientFallback} returns an empty vector so the
 * main flow is never blocked by embedding unavailability.
 */
@FeignClient(
    name = "ai-analysis-service",
    url = "${feign.client.ai-analysis.url:http://localhost:8083}",
    fallback = AiAnalysisClientFallback.class)
public interface AiAnalysisClient {

  @PostMapping("/internal/embedding")
  EmbeddingResponse embed(@RequestBody EmbeddingRequest request);

  record EmbeddingRequest(Long wrongItemId, String text) {}

  record EmbeddingResponse(Long wrongItemId, List<Float> embedding) {}
}
