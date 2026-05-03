package com.longfeng.wrongbook.support;

import com.longfeng.wrongbook.client.AiAnalysisClient;
import com.longfeng.wrongbook.client.AiAnalysisClient.EmbeddingRequest;
import com.longfeng.wrongbook.client.AiAnalysisClient.EmbeddingResponse;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Asynchronous embedding worker — triggered after a WrongItem is created/updated.
 *
 * <p>Design decisions:
 * <ul>
 *   <li>Uses {@code @Async} so the main write transaction completes before embedding is attempted.
 *   <li>Calls {@link AiAnalysisClient#embed} — Feign client with Sentinel fallback (C10).
 *   <li>Updates {@code wrong_item.embedding} via native JDBC (pgvector type not mapped in JPA entity).
 *   <li>Any failure is logged and swallowed — embedding is best-effort in Phase S4.
 * </ul>
 *
 * <p>Phase 2 TODO: replace stub with real ai-analysis-service once deployed (caveat C-14).
 */
@Component
public class EmbeddingAsyncWorker {

  private static final Logger LOG = LoggerFactory.getLogger(EmbeddingAsyncWorker.class);

  private final AiAnalysisClient aiClient;
  private final JdbcTemplate jdbc;

  public EmbeddingAsyncWorker(AiAnalysisClient aiClient, JdbcTemplate jdbc) {
    this.aiClient = aiClient;
    this.jdbc = jdbc;
  }

  /**
   * Generate and persist embedding for a WrongItem asynchronously.
   *
   * @param wrongItemId the item id
   * @param text the OCR/stem text to embed (may be null, handled gracefully)
   */
  @Async
  public void generateAndPersist(Long wrongItemId, String text) {
    if (text == null || text.isBlank()) {
      LOG.debug("EmbeddingAsyncWorker · skip itemId={} (blank text)", wrongItemId);
      return;
    }
    try {
      EmbeddingResponse resp = aiClient.embed(new EmbeddingRequest(wrongItemId, text));
      List<Float> embedding = resp.embedding();
      if (embedding == null || embedding.isEmpty()) {
        LOG.debug("EmbeddingAsyncWorker · empty embedding returned for itemId={}", wrongItemId);
        return;
      }
      // Convert float list to pgvector string format: '[0.1,0.2,...]'
      String vectorLiteral = buildVectorLiteral(embedding);
      int updated = jdbc.update(
          "UPDATE wrong_item SET embedding = ?::vector WHERE id = ?",
          vectorLiteral, wrongItemId);
      if (updated == 0) {
        LOG.warn("EmbeddingAsyncWorker · no row updated for itemId={} (deleted?)", wrongItemId);
      } else {
        LOG.info("EmbeddingAsyncWorker · embedding persisted for itemId={} dim={}",
            wrongItemId, embedding.size());
      }
    } catch (Exception ex) {
      // Best-effort — never propagate to block the caller thread pool
      LOG.warn("EmbeddingAsyncWorker · failed for itemId={}: {}", wrongItemId, ex.getMessage());
    }
  }

  private static String buildVectorLiteral(List<Float> v) {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < v.size(); i++) {
      if (i > 0) sb.append(',');
      sb.append(v.get(i));
    }
    sb.append(']');
    return sb.toString();
  }
}
