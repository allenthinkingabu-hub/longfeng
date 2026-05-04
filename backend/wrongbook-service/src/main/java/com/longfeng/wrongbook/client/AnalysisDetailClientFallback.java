package com.longfeng.wrongbook.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Fallback for {@link AnalysisDetailClient} · returns {@code null} so the aggregate service can
 * substitute mock data while logging a warning (so the prod fallback never goes silent — see
 * QA bullet "BE-08 mock-fallback warning location").
 */
@Component
public class AnalysisDetailClientFallback implements AnalysisDetailClient {

  private static final Logger LOG = LoggerFactory.getLogger(AnalysisDetailClientFallback.class);

  @Override
  public AnalysisDetailResponse latest(Long itemId) {
    LOG.warn(
        "ai-analysis-service unavailable · falling back to mock for itemId={} (P04 detail)",
        itemId);
    return null;
  }
}
