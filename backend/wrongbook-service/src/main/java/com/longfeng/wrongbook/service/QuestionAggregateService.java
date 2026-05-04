package com.longfeng.wrongbook.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.longfeng.wrongbook.client.AnalysisDetailClient;
import com.longfeng.wrongbook.client.AnalysisDetailClient.AnalysisDetailResponse;
import com.longfeng.wrongbook.client.ReviewPlanClient;
import com.longfeng.wrongbook.client.ReviewPlanClient.CreatePlanRequest;
import com.longfeng.wrongbook.client.ReviewPlanClient.CreatePlanResponse;
import com.longfeng.wrongbook.dto.PlannedNodeDto;
import com.longfeng.wrongbook.dto.QuestionDetailDto;
import com.longfeng.wrongbook.dto.QuestionDetailDto.KnowledgePoint;
import com.longfeng.wrongbook.dto.QuestionDetailDto.ModelInfo;
import com.longfeng.wrongbook.dto.QuestionDetailDto.SolutionStep;
import com.longfeng.wrongbook.dto.QuestionDetailResp;
import com.longfeng.wrongbook.dto.SaveQuestionResp;
import com.longfeng.wrongbook.dto.SaveQuestionResp.PlanNode;
import com.longfeng.wrongbook.entity.WrongAttempt;
import com.longfeng.wrongbook.entity.WrongItem;
import com.longfeng.wrongbook.entity.WrongItemTag;
import com.longfeng.wrongbook.repo.WrongAttemptRepository;
import com.longfeng.wrongbook.repo.WrongItemRepository;
import com.longfeng.wrongbook.repo.WrongItemTagRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aggregator for the P04 Result page · resolves wrong_item + last attempt + tags + AI analysis
 * into a single VO {@link QuestionDetailResp}, and orchestrates "save and start review" by
 * calling review-plan-service.
 *
 * <p>Mock-fallback behaviour: when the wrong_item exists but AI analysis is missing or fields are
 * incomplete, this service substitutes mock-friendly defaults (so FE never crashes) and writes a
 * WARN log so prod operators can see the silent fallback (BE-08 mock-fallback-warning location).
 * If the wrong_item itself is missing, a {@link WrongItemService.NotFoundException} is raised so
 * the controller maps it to HTTP 404.
 */
@Service
public class QuestionAggregateService {

  private static final Logger LOG = LoggerFactory.getLogger(QuestionAggregateService.class);

  /** SM-2 default offsets in days for T1..T6 (mirrors review-plan-service NODE_OFFSETS). */
  private static final long[] OFFSET_DAYS = {1, 2, 4, 7, 14, 30};

  private final WrongItemRepository itemRepo;
  private final WrongAttemptRepository attemptRepo;
  private final WrongItemTagRepository tagRepo;
  private final AnalysisDetailClient analysisClient;
  private final ReviewPlanClient reviewPlanClient;
  private final ObjectMapper objectMapper;

  public QuestionAggregateService(
      WrongItemRepository itemRepo,
      WrongAttemptRepository attemptRepo,
      WrongItemTagRepository tagRepo,
      AnalysisDetailClient analysisClient,
      ReviewPlanClient reviewPlanClient,
      ObjectMapper objectMapper) {
    this.itemRepo = itemRepo;
    this.attemptRepo = attemptRepo;
    this.tagRepo = tagRepo;
    this.analysisClient = analysisClient;
    this.reviewPlanClient = reviewPlanClient;
    this.objectMapper = objectMapper;
  }

  /**
   * Get the P04 detail aggregate for the supplied {@code qid} (== wrong_item id).
   *
   * @throws WrongItemService.NotFoundException when the wrong_item does not exist (HTTP 404).
   */
  @Transactional(readOnly = true)
  public QuestionDetailResp getDetail(String qid) {
    long itemId = parseId(qid);
    WrongItem item =
        itemRepo
            .findById(itemId)
            .orElseThrow(
                () ->
                    new WrongItemService.NotFoundException("wrong_item not found: " + qid));

    Optional<WrongAttempt> lastAttempt =
        attemptRepo
            .findByWrongItemIdOrderBySubmittedAtDescIdDesc(itemId, Limit.of(1))
            .stream()
            .findFirst();
    List<WrongItemTag> tags = tagRepo.findByWrongItemIdOrderByIdAsc(itemId);

    AnalysisDetailResponse analysis = safeFetchAnalysis(itemId);

    QuestionDetailDto question = buildQuestionDto(item, lastAttempt.orElse(null), tags, analysis);
    List<PlannedNodeDto> preview = buildPreviewNodes(Instant.now());
    return new QuestionDetailResp(question, preview);
  }

  /**
   * Save the wrong_item to the user's wrongbook and trigger SM-2 plan creation. The save itself
   * is idempotent at the wrong_item level (item already exists from analyze stage); this method
   * just promotes status and asks review-plan-service to materialise T1..T6.
   */
  @Transactional
  public SaveQuestionResp save(String qid, String requestId) {
    long itemId = parseId(qid);
    WrongItem item =
        itemRepo
            .findById(itemId)
            .orElseThrow(
                () ->
                    new WrongItemService.NotFoundException("wrong_item not found: " + qid));

    // Ask review-plan-service to materialise SM-2 plan + 6 nodes. If unreachable, the Sentinel
    // fallback returns a deterministic preview and emits a WARN — so save still succeeds (outbox
    // pattern: the actual plan creation is then expected to be replayed from wrong_item_outbox).
    CreatePlanResponse plan;
    try {
      plan =
          reviewPlanClient.create(
              new CreatePlanRequest(
                  String.valueOf(item.getId()),
                  String.valueOf(item.getStudentId()),
                  item.getSubject()),
              requestId);
    } catch (Exception ex) {
      LOG.warn(
          "review-plan-service create failed · falling back to preview · itemId={} · err={}",
          itemId,
          ex.getMessage());
      plan = fallbackPlan(item);
    }

    return new SaveQuestionResp(
        qid,
        plan.planId(),
        plan.nodes().stream()
            .map(n -> new PlanNode(n.nid(), n.tLevel(), n.dueAt()))
            .toList());
  }

  // ---------- internals ----------

  private AnalysisDetailResponse safeFetchAnalysis(long itemId) {
    try {
      return analysisClient.latest(itemId);
    } catch (Exception ex) {
      LOG.warn(
          "ai-analysis-service GET /analysis/{} failed · falling back to mock · err={}",
          itemId,
          ex.getMessage());
      return null;
    }
  }

  private QuestionDetailDto buildQuestionDto(
      WrongItem item,
      WrongAttempt lastAttempt,
      List<WrongItemTag> tags,
      AnalysisDetailResponse analysis) {

    String subject = item.getSubject() != null ? item.getSubject() : "math";
    String stem = item.getStemText() != null ? item.getStemText() : item.getOcrText();
    if (stem == null || stem.isBlank()) {
      LOG.warn(
          "wrong_item {} has empty stem · using mock placeholder (BE-08 fallback)", item.getId());
      stem = "(题干暂未识别)";
    }

    String myAnswer = lastAttempt != null && lastAttempt.getAnswerText() != null
        ? lastAttempt.getAnswerText()
        : "(暂无作答)";
    String correctAnswer = "(待 AI 给出)";

    String reasonMarkdown;
    String causeTag;
    List<SolutionStep> steps;
    Double confidence;
    ModelInfo modelInfo;

    if (analysis != null && analysis.explain() != null && !analysis.explain().isBlank()) {
      reasonMarkdown = analysis.explain();
      causeTag = analysis.causeTag();
      steps = parseSolutionSteps(analysis.solutionSteps());
      confidence = analysis.status() != null && analysis.status().equals("success") ? 0.9 : 0.5;
      modelInfo =
          new ModelInfo(
              analysis.modelName() != null ? analysis.modelName() : "qwen-vl-max",
              String.valueOf(analysis.version()));
    } else {
      LOG.warn(
          "wrong_item {} has no AI analysis · returning mock fallback so FE renders · "
              + "this should never appear in prod once ai-analysis-service is wired.",
          item.getId());
      reasonMarkdown = "(AI 分析尚未就绪 · 请稍后重试)";
      causeTag = "OTHER";
      steps = List.of();
      confidence = 0.5;
      modelInfo = new ModelInfo("stub", "0");
    }

    List<KnowledgePoint> knowledgePoints =
        tags.stream()
            .map(
                t ->
                    new KnowledgePoint(
                        t.getTagCode(),
                        // tag display name not joined here · FE shows code as fallback
                        t.getTagCode(),
                        t.getWeight() != null ? t.getWeight().doubleValue() : 1.0))
            .toList();

    Integer difficulty = item.getDifficulty() != null ? item.getDifficulty().intValue() : 3;

    String thumbnailUrl =
        item.getProcessedImageKey() != null
            ? item.getProcessedImageKey()
            : item.getOriginImageKey();

    return new QuestionDetailDto(
        String.valueOf(item.getId()),
        subject,
        stem,
        null, // formula (optional · derived only when LLM returns explicit formula token)
        thumbnailUrl,
        myAnswer,
        correctAnswer,
        reasonMarkdown,
        steps,
        knowledgePoints,
        difficulty,
        confidence,
        modelInfo);
  }

  /**
   * Best-effort parse of the AI {@code solution_steps} JSON into typed steps. The AI service
   * passes the JSONB straight through · expected shape is an array of objects · we tolerate
   * unexpected shapes by returning an empty list (and logging once).
   */
  private List<SolutionStep> parseSolutionSteps(Object raw) {
    if (raw == null) {
      return List.of();
    }
    try {
      List<Map<String, Object>> arr =
          objectMapper.convertValue(raw, new TypeReference<List<Map<String, Object>>>() {});
      List<SolutionStep> out = new ArrayList<>();
      int idx = 1;
      for (Map<String, Object> entry : arr) {
        Object idxObj = entry.get("idx");
        int stepIdx = idxObj instanceof Number n ? n.intValue() : idx;
        out.add(
            new SolutionStep(
                stepIdx,
                String.valueOf(entry.getOrDefault("title", entry.getOrDefault("text", ""))),
                entry.get("detail") != null ? String.valueOf(entry.get("detail")) : null,
                entry.get("formula") != null ? String.valueOf(entry.get("formula")) : null));
        idx++;
      }
      return out;
    } catch (Exception ex) {
      LOG.warn("solution_steps unrecognised shape · ignoring · err={}", ex.getMessage());
      return List.of();
    }
  }

  private List<PlannedNodeDto> buildPreviewNodes(Instant base) {
    List<PlannedNodeDto> out = new ArrayList<>(6);
    for (int i = 0; i < OFFSET_DAYS.length; i++) {
      Instant due = base.plus(Duration.ofDays(OFFSET_DAYS[i]));
      out.add(new PlannedNodeDto("T" + (i + 1), due.toString(), "preview"));
    }
    return out;
  }

  private CreatePlanResponse fallbackPlan(WrongItem item) {
    Instant base = Instant.now();
    List<CreatePlanResponse.NodePreview> nodes = new ArrayList<>(6);
    for (int i = 0; i < OFFSET_DAYS.length; i++) {
      Instant due = base.plus(Duration.ofDays(OFFSET_DAYS[i]));
      nodes.add(
          new CreatePlanResponse.NodePreview(
              "preview-" + item.getId() + "-T" + (i + 1), "T" + (i + 1), due.toString()));
    }
    return new CreatePlanResponse("outbox-" + item.getId(), nodes);
  }

  private static long parseId(String qid) {
    if (qid == null || qid.isBlank()) {
      throw new WrongItemService.NotFoundException("wrong_item id is blank");
    }
    try {
      return Long.parseLong(qid.trim());
    } catch (NumberFormatException ex) {
      throw new WrongItemService.NotFoundException("wrong_item not found: " + qid);
    }
  }
}
