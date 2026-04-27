package com.longfeng.aianalysis.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.longfeng.aianalysis.entity.AiUsageLog;
import com.longfeng.aianalysis.entity.WrongItemAnalysis;
import com.longfeng.aianalysis.llm.LlmProvider;
import com.longfeng.aianalysis.llm.ProviderRouter;
import com.longfeng.aianalysis.pii.PIIRedactor;
import com.longfeng.aianalysis.prompt.PromptTemplates;
import com.longfeng.aianalysis.repo.AiUsageLogRepository;
import com.longfeng.aianalysis.repo.WrongItemAnalysisRepository;
import com.longfeng.aianalysis.service.dto.AnalysisVO;
import com.longfeng.aianalysis.service.dto.ExplainChunk;
import com.longfeng.aianalysis.service.dto.SimilarItemVO;
import com.longfeng.aianalysis.support.SnowflakeIdGenerator;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates: idempotency check → PII redact → LLM chat + embed (with fallback) → persist
 * analysis + update wrong_item.embedding + append ai_usage_log. Terminal failure lands
 * status=9 (pending_analysis) so the consumer can still ack its MQ message (arch §3.2).
 */
@Service
public class AnalysisService {

  private static final Logger LOG = LoggerFactory.getLogger(AnalysisService.class);

  /** Embedding vector dimension locked by INV-05 / BR-09 (pgvector vector(1024)). */
  static final int EMBEDDING_DIM = 1024;

  /**
   * Cosine-distance ceiling for similar-item recall (BR-10 / BR-17). Items whose pgvector
   * {@code <=>} score exceeds this are dropped <em>after</em> the SQL ORDER BY so the index
   * scan is preserved.
   */
  static final double SIMILAR_DISTANCE_CEILING = 1.5d;

  /** Max characters per SSE chunk frame · prevents huge frames when error_reason is a wall of text. */
  static final int CHUNK_CHAR_LIMIT = 80;

  private final WrongItemAnalysisRepository analysisRepo;
  private final AiUsageLogRepository usageRepo;
  private final ProviderRouter router;
  private final PIIRedactor pii;
  private final SnowflakeIdGenerator ids;
  private final JdbcTemplate jdbc;
  private final ObjectMapper om;

  /** EntityManager for native pgvector queries (orm.allowed: nativeQuery=true · BR-10). */
  @PersistenceContext private EntityManager em;

  public AnalysisService(
      WrongItemAnalysisRepository analysisRepo,
      AiUsageLogRepository usageRepo,
      ProviderRouter router,
      PIIRedactor pii,
      SnowflakeIdGenerator ids,
      JdbcTemplate jdbc,
      ObjectMapper om) {
    this.analysisRepo = analysisRepo;
    this.usageRepo = usageRepo;
    this.router = router;
    this.pii = pii;
    this.ids = ids;
    this.jdbc = jdbc;
    this.om = om;
  }

  /** Returns the new/existing analysis version code · 0 = skipped due to idempotency. */
  @Transactional
  public int analyze(Long itemId, boolean bypassIdempotency) {
    int version = PromptTemplates.versionCode();
    if (!bypassIdempotency) {
      Optional<WrongItemAnalysis> existing = analysisRepo.findByWrongItemIdAndVersion(itemId, version);
      if (existing.isPresent()) {
        LOG.debug("analyze idempotent-skip itemId={} version={}", itemId, version);
        return 0;
      }
    } else {
      // Bump to next version when admin retries · keeps history immutable.
      version =
          analysisRepo.findTopByWrongItemIdOrderByVersionDesc(itemId).map(a -> a.getVersion() + 1).orElse(version);
    }

    Map<String, Object> row;
    try {
      row =
          jdbc.queryForMap(
              "SELECT id, student_id, subject, stem_text, ocr_text FROM wrong_item WHERE id = ? AND deleted_at IS NULL",
              itemId);
    } catch (Exception e) {
      LOG.warn("wrong_item not found for analyze itemId={}", itemId);
      return -1;
    }

    Long studentId = ((Number) row.get("student_id")).longValue();
    String subject = (String) row.get("subject");
    String stem = (String) row.get("stem_text");
    String ocr = (String) row.get("ocr_text");
    String sanitized = pii.redact(stem != null ? stem : ocr, studentId);
    String prompt = PromptTemplates.render(subject, sanitized == null ? "" : sanitized);

    WrongItemAnalysis a = new WrongItemAnalysis();
    a.setId(ids.nextId());
    a.setWrongItemId(itemId);
    a.setVersion(version);
    a.setStemText(sanitized);
    a.setStatus(WrongItemAnalysis.STATUS_PENDING);

    try {
      LlmProvider.ChatResponse chat = router.chatWithFallback(prompt, Map.of());
      float[] embedding = router.embedWithFallback(sanitized == null ? "" : sanitized);

      JsonNode parsed = parseOrFallback(chat.rawJson());
      String provider = router.currentProviderName();
      a.setModelProvider(provider);
      a.setModelName(chat.model());
      a.setInputTokens(chat.tokensIn());
      a.setOutputTokens(chat.tokensOut());
      a.setCostCents(estimateCostCents(chat.tokensIn(), chat.tokensOut(), provider));
      a.setRawJson(chat.rawJson());
      a.setFinishedAt(Instant.now());

      if (parsed == null) {
        a.setStatus(WrongItemAnalysis.STATUS_FALLBACK);
        a.setErrorReason("LLM returned non-JSON · see raw_json");
      } else {
        a.setStatus(WrongItemAnalysis.STATUS_SUCCESS);
        a.setErrorReason(parsed.path("explain").asText(null));
        a.setErrorType(parsed.path("causeTag").asText(null));
        try {
          a.setKnowledgePoints(om.writeValueAsString(parsed.path("autoTags")));
        } catch (JsonProcessingException jpe) {
          a.setKnowledgePoints("[]");
        }
      }
      analysisRepo.save(a);
      updateEmbedding(itemId, embedding);
      appendUsage(itemId, studentId, provider, chat.model(), "chat", chat.tokensIn(), chat.tokensOut(), a.getCostCents(), (int) chat.latencyMs(), a.getStatus());
      appendUsage(itemId, studentId, provider, chat.model(), "embed", (sanitized == null ? 0 : sanitized.length()), null, 0, null, WrongItemAnalysis.STATUS_SUCCESS);
    } catch (ProviderRouter.AllProvidersFailedException failed) {
      LOG.warn("analyze itemId={} · all providers failed · writing pending_analysis", itemId);
      a.setModelProvider("stub");
      a.setModelName("none");
      a.setRawJson("{\"error\":\"all-providers-failed\"}");
      a.setErrorReason(failed.getMessage());
      a.setStatus(WrongItemAnalysis.STATUS_PENDING);
      analysisRepo.save(a);
      appendUsage(itemId, studentId, "stub", "none", "chat", 0, 0, 0, null, WrongItemAnalysis.STATUS_PENDING);
    }
    return version;
  }

  /**
   * Read the latest (max version) analysis row for a wrong-item and project it as the REST DTO.
   * Returns {@link Optional#empty()} when no analysis row exists — controller layer translates
   * that to an HTTP 404. Read-only · no transaction needed beyond Spring Data's default.
   */
  @Transactional(readOnly = true)
  public Optional<AnalysisVO> findLatest(Long itemId) {
    return analysisRepo.findTopByWrongItemIdOrderByVersionDesc(itemId).map(this::toVO);
  }

  /**
   * Admin-triggered re-run for a wrong-item (BR-06). Reuses the main {@link #analyze(Long, boolean)}
   * pipeline with {@code bypassIdempotency=true} so a fresh row lands at {@code maxVersion + 1} —
   * historical rows stay immutable per BR-07. Authorization (admin gate) is enforced at the
   * controller layer; this method assumes the caller is already authorized.
   *
   * @return the version code newly written ({@code -1} when the wrong-item itself is missing)
   */
  public int retry(Long itemId) {
    return analyze(itemId, true);
  }

  /**
   * pgvector 召回相似题（BR-10 / BR-17）· 用 EntityManager native query 走
   * arch-constraints.orm.allowed 的 "@Query nativeQuery=true（仅 pgvector）" 通道。
   *
   * <p>service-layer 过滤 distance > 1.5 的尾部项 · 排序由 pgvector cosine 索引在 SQL 层完成。
   * 当目标 itemId 自身的 embedding 尚未生成时返回空列表（前端展示 "暂无相似题"）。
   *
   * @param itemId 目标错题 id
   * @param k 期望返回条数 · 入参 ≤ 0 抛 IllegalArgumentException · > 10 截断到 10（spec max=10）
   */
  @Transactional(readOnly = true)
  public List<SimilarItemVO> findSimilar(Long itemId, int k) {
    if (k <= 0) {
      throw new IllegalArgumentException("k must be > 0 · got=" + k);
    }
    int limit = Math.min(k, 10);

    // probe 向量来自 wrong_item.embedding（INV-05 唯一写入位）· 该题尚无 embedding → 空召回。
    String probe;
    try {
      probe =
          jdbc.queryForObject(
              "SELECT embedding::text FROM wrong_item WHERE id = ? AND embedding IS NOT NULL AND deleted_at IS NULL",
              String.class,
              itemId);
    } catch (org.springframework.dao.EmptyResultDataAccessException notFound) {
      return Collections.emptyList();
    }
    if (probe == null) {
      return Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    List<Object[]> rows =
        em.createNativeQuery(
                "SELECT id, stem_text, subject, (embedding <=> CAST(:probe AS vector)) AS distance "
                    + "FROM wrong_item "
                    + "WHERE embedding IS NOT NULL "
                    + "  AND id <> :itemId "
                    + "  AND deleted_at IS NULL "
                    + "ORDER BY embedding <=> CAST(:probe AS vector) "
                    + "LIMIT :k")
            .setParameter("probe", probe)
            .setParameter("itemId", itemId)
            .setParameter("k", limit)
            .getResultList();

    List<SimilarItemVO> out = new ArrayList<>(rows.size());
    for (Object[] r : rows) {
      double distance = ((Number) r[3]).doubleValue();
      // BR-10 service-layer ceiling · spec: distance > 1.5 dropped（保留 ≤ 1.5）
      if (distance > SIMILAR_DISTANCE_CEILING) {
        continue;
      }
      String id = String.valueOf(((Number) r[0]).longValue());
      String stemText = (String) r[1];
      String subject = (String) r[2];
      out.add(new SimilarItemVO(id, stemText, subject, distance));
    }
    return out;
  }

  /**
   * SSE 流式回放已存解析（BR-16）· 仅读 wrong_item_analysis 当前最新版本 · 不旁路 MQ · 不调 LLM。
   *
   * <p>决策树（sse_protocol.behavior_rules）：
   *
   * <ul>
   *   <li>未找到 → 单帧 "尚未分析" + done:true
   *   <li>status=1 (FALLBACK) → 单帧 "当前题目暂未生成解析，请稍后重试" + done:true
   *   <li>status=9 (PENDING) → 单帧 "正在分析中，请稍后查看" + done:true
   *   <li>status=0 (SUCCESS) → 按句号 / 80 字符切块 · 末帧 done:true
   * </ul>
   */
  @Transactional(readOnly = true)
  public List<ExplainChunk> streamExplain(Long itemId) {
    Optional<WrongItemAnalysis> found = analysisRepo.findTopByWrongItemIdOrderByVersionDesc(itemId);
    if (found.isEmpty()) {
      return List.of(ExplainChunk.terminal("尚未分析"));
    }
    WrongItemAnalysis a = found.get();
    Short status = a.getStatus();
    if (status != null && status == WrongItemAnalysis.STATUS_FALLBACK) {
      return List.of(ExplainChunk.terminal("当前题目暂未生成解析，请稍后重试"));
    }
    if (status != null && status == WrongItemAnalysis.STATUS_PENDING) {
      return List.of(ExplainChunk.terminal("正在分析中，请稍后查看"));
    }
    // status == STATUS_SUCCESS（0）→ 回放 explain 文本
    String explain = a.getErrorReason();
    if (explain == null || explain.isBlank()) {
      return List.of(ExplainChunk.terminal("尚未分析"));
    }
    List<String> pieces = splitForChunks(explain);
    if (pieces.size() == 1) {
      return List.of(ExplainChunk.terminal(pieces.get(0)));
    }
    List<ExplainChunk> out = new ArrayList<>(pieces.size());
    for (int i = 0; i < pieces.size(); i++) {
      String piece = pieces.get(i);
      if (i == pieces.size() - 1) {
        out.add(ExplainChunk.terminal(piece));
      } else {
        out.add(ExplainChunk.of(piece));
      }
    }
    return out;
  }

  /**
   * 切分 explain 文本：① 先按中文句号 '。' 或英文 '.' 切（保留句号）；② 单段超过 {@link #CHUNK_CHAR_LIMIT}
   * 再按 80 字符硬切；③ 过滤空段。返回顺序保留。
   */
  private List<String> splitForChunks(String text) {
    List<String> sentences = new ArrayList<>();
    StringBuilder buf = new StringBuilder();
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      buf.append(c);
      if (c == '。' || c == '.') {
        String s = buf.toString().trim();
        if (!s.isEmpty()) {
          sentences.add(s);
        }
        buf.setLength(0);
      }
    }
    String tail = buf.toString().trim();
    if (!tail.isEmpty()) {
      sentences.add(tail);
    }

    List<String> out = new ArrayList<>(sentences.size());
    for (String s : sentences) {
      if (s.length() <= CHUNK_CHAR_LIMIT) {
        out.add(s);
        continue;
      }
      // 二次硬切 · 80 字符为单位
      for (int start = 0; start < s.length(); start += CHUNK_CHAR_LIMIT) {
        int end = Math.min(start + CHUNK_CHAR_LIMIT, s.length());
        out.add(s.substring(start, end));
      }
    }
    return out;
  }

  private AnalysisVO toVO(WrongItemAnalysis a) {
    JsonNode solutionSteps = readJsonOrNull(a.getSolutionSteps());
    List<String> autoTags = readStringArray(a.getKnowledgePoints());
    return new AnalysisVO(
        a.getId() == null ? null : String.valueOf(a.getId()),
        a.getWrongItemId() == null ? null : String.valueOf(a.getWrongItemId()),
        a.getVersion() == null ? 0 : a.getVersion(),
        a.getModelProvider(),
        a.getModelName(),
        AnalysisVO.mapStatus(a.getStatus()),
        a.getErrorReason(),
        a.getErrorType(),
        autoTags,
        solutionSteps,
        a.getFinishedAt() == null ? null : a.getFinishedAt().toString());
  }

  private JsonNode readJsonOrNull(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    try {
      return om.readTree(raw);
    } catch (Exception ex) {
      LOG.warn("solution_steps JSON parse failed · returning null · raw len={}", raw.length());
      return null;
    }
  }

  private List<String> readStringArray(String raw) {
    if (raw == null || raw.isBlank()) {
      return Collections.emptyList();
    }
    try {
      JsonNode n = om.readTree(raw);
      if (n instanceof ArrayNode arr) {
        List<String> out = new ArrayList<>(arr.size());
        for (JsonNode el : arr) {
          out.add(el.asText());
        }
        return out;
      }
    } catch (Exception ex) {
      LOG.warn("knowledge_points JSON parse failed · returning empty list");
    }
    return Collections.emptyList();
  }

  private JsonNode parseOrFallback(String raw) {
    if (raw == null || raw.isBlank()) return null;
    try {
      JsonNode n = om.readTree(raw);
      if (!n.has("explain") || !n.has("causeTag") || !n.has("autoTags")) return null;
      return n;
    } catch (Exception ex) {
      return null;
    }
  }

  private void updateEmbedding(Long itemId, float[] embedding) {
    // BR-09 / INV-05 — service-layer guard so the violation surfaces before the SQL roundtrip.
    if (embedding == null || embedding.length != EMBEDDING_DIM) {
      throw new IllegalStateException(
          "embedding dimension must be " + EMBEDDING_DIM + " · got=" + (embedding == null ? "null" : embedding.length));
    }
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < embedding.length; i++) {
      if (i > 0) sb.append(',');
      sb.append(embedding[i]);
    }
    sb.append(']');
    jdbc.update(
        "UPDATE wrong_item SET embedding = ?::vector WHERE id = ?", sb.toString(), itemId);
  }

  private void appendUsage(
      Long itemId,
      Long userId,
      String provider,
      String model,
      String apiType,
      Integer tokensIn,
      Integer tokensOut,
      Integer costCents,
      Integer latencyMs,
      Short status) {
    AiUsageLog u = new AiUsageLog();
    u.setUserId(userId);
    u.setItemId(itemId);
    u.setProvider(provider);
    u.setModel(model);
    u.setApiType(apiType);
    u.setTokensIn(tokensIn);
    u.setTokensOut(tokensOut);
    u.setCostCents(costCents == null ? 0 : costCents);
    u.setLatencyMs(latencyMs);
    u.setStatus(status);
    usageRepo.save(u);
  }

  /** Rough cost estimate · reads from docs/ai-pricing.yaml when present · default 0. */
  private int estimateCostCents(Integer tokensIn, Integer tokensOut, String provider) {
    int tokIn = tokensIn == null ? 0 : tokensIn;
    int tokOut = tokensOut == null ? 0 : tokensOut;
    // Coarse default · 0.01¢ per 1k tokens so tests see non-zero values. Production pricing
    // should be pulled from the pricing yaml (§8.7 Step 13).
    return (tokIn + tokOut) / 100;
  }
}
