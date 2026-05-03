package com.longfeng.wrongbook.client;

import java.util.Collections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Sentinel fallback for {@link AiAnalysisClient}.
 * Returns an empty embedding list so embedding failure never blocks the main write path.
 */
@Component
public class AiAnalysisClientFallback implements AiAnalysisClient {

  private static final Logger LOG = LoggerFactory.getLogger(AiAnalysisClientFallback.class);

  @Override
  public EmbeddingResponse embed(EmbeddingRequest request) {
    LOG.warn("ai-analysis-service unavailable · embedding skipped for itemId={}", request.wrongItemId());
    return new EmbeddingResponse(request.wrongItemId(), Collections.emptyList());
  }
}
