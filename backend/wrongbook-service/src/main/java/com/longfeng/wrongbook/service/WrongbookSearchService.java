package com.longfeng.wrongbook.service;

import com.longfeng.wrongbook.dto.WrongItemVO;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hybrid search over wrong_item using pgvector (semantic) + pg_trgm (keyword).
 *
 * <p>Algorithm: Reciprocal Rank Fusion (RRF).
 * <ul>
 *   <li>Run vector ANN search (cosine similarity via pgvector &lt;=&gt; operator).
 *   <li>Run trigram text search (pg_trgm similarity on ocr_text + stem_text).
 *   <li>Combine with RRF: score = sum(1 / (k + rank_i)) where k=60 (standard RRF constant).
 *   <li>Return top-N results ordered by descending RRF score.
 * </ul>
 *
 * <p>NOTE: embedding column is float[] in pgvector; query embedding must be passed as a vector
 * literal string {@code '[0.1,0.2,...]'} cast to {@code ::vector}.
 * If no query embedding is provided, falls back to trigram-only search.
 */
@Service
public class WrongbookSearchService {

  /** RRF constant — lower k = more weight on top-ranked results. */
  private static final int RRF_K = 60;

  private static final int MAX_CANDIDATES = 200; // candidates per sub-search
  private static final int DEFAULT_TOP_N = 20;

  private final JdbcTemplate jdbc;
  private final WrongItemService itemService;

  public WrongbookSearchService(JdbcTemplate jdbc, WrongItemService itemService) {
    this.jdbc = jdbc;
    this.itemService = itemService;
  }

  /**
   * Perform hybrid search and return merged, RRF-ranked results.
   *
   * @param query        raw text query (used for trigram search)
   * @param queryVector  optional pre-computed embedding for semantic search (may be null)
   * @param studentId    optional student filter
   * @param subject      optional subject filter
   * @param topN         number of results to return (capped at 100)
   */
  @Transactional(readOnly = true)
  public List<WrongItemVO> hybridSearch(
      String query,
      List<Float> queryVector,
      Long studentId,
      String subject,
      int topN) {

    int limit = Math.min(Math.max(topN, 1), 100);

    Map<Long, Double> rrfScores = new HashMap<>();

    // --- trigram search (always run if query is non-blank) ---
    if (query != null && !query.isBlank()) {
      List<Long> trigramIds = trigramSearch(query, studentId, subject, MAX_CANDIDATES);
      mergeRRF(rrfScores, trigramIds);
    }

    // --- vector search (run if embedding is provided and non-empty) ---
    if (queryVector != null && !queryVector.isEmpty()) {
      List<Long> vectorIds = vectorSearch(queryVector, studentId, subject, MAX_CANDIDATES);
      mergeRRF(rrfScores, vectorIds);
    }

    if (rrfScores.isEmpty()) {
      return List.of();
    }

    // Sort by RRF score desc, take top-N
    List<Long> topIds = rrfScores.entrySet().stream()
        .sorted(Map.Entry.<Long, Double>comparingByValue(Comparator.reverseOrder()))
        .limit(limit)
        .map(Map.Entry::getKey)
        .toList();

    // Load full VOs preserving RRF order
    return topIds.stream()
        .map(id -> itemService.findById(id))
        .filter(opt -> opt.isPresent())
        .map(opt -> opt.get())
        .toList();
  }

  // ---- internal ----

  private List<Long> trigramSearch(String query, Long studentId, String subject, int limit) {
    StringBuilder sql = new StringBuilder(
        "SELECT id FROM wrong_item WHERE deleted_at IS NULL ");
    List<Object> params = new ArrayList<>();

    sql.append("AND (similarity(ocr_text, ?) + similarity(stem_text, ?)) > 0.1 ");
    params.add(query);
    params.add(query);

    if (studentId != null) {
      sql.append("AND student_id = ? ");
      params.add(studentId);
    }
    if (subject != null && !subject.isBlank()) {
      sql.append("AND subject = ? ");
      params.add(subject);
    }

    sql.append("ORDER BY (similarity(ocr_text, ?) + similarity(stem_text, ?)) DESC ");
    params.add(query);
    params.add(query);

    sql.append("LIMIT ?");
    params.add(limit);

    return jdbc.queryForList(sql.toString(), Long.class, params.toArray());
  }

  private List<Long> vectorSearch(List<Float> queryVector, Long studentId, String subject, int limit) {
    String vectorLiteral = toVectorLiteral(queryVector);
    StringBuilder sql = new StringBuilder(
        "SELECT id FROM wrong_item WHERE deleted_at IS NULL AND embedding IS NOT NULL ");
    List<Object> params = new ArrayList<>();

    if (studentId != null) {
      sql.append("AND student_id = ? ");
      params.add(studentId);
    }
    if (subject != null && !subject.isBlank()) {
      sql.append("AND subject = ? ");
      params.add(subject);
    }

    sql.append("ORDER BY embedding <=> ?::vector LIMIT ?");
    params.add(vectorLiteral);
    params.add(limit);

    return jdbc.queryForList(sql.toString(), Long.class, params.toArray());
  }

  /** Merge ranked id list into RRF score map. rank starts at 1. */
  private static void mergeRRF(Map<Long, Double> scores, List<Long> ids) {
    for (int rank = 0; rank < ids.size(); rank++) {
      long id = ids.get(rank);
      double contribution = 1.0 / (RRF_K + rank + 1);
      scores.merge(id, contribution, Double::sum);
    }
  }

  static String toVectorLiteral(List<Float> v) {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < v.size(); i++) {
      if (i > 0) sb.append(',');
      sb.append(v.get(i));
    }
    sb.append(']');
    return sb.toString();
  }
}
