package com.longfeng.aianalysis.controller;

import com.longfeng.aianalysis.entity.WrongItemAnalysis;
import com.longfeng.aianalysis.llm.ProviderRouter;
import com.longfeng.aianalysis.repo.WrongItemAnalysisRepository;
import com.longfeng.aianalysis.service.AnalysisService;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/analysis")
public class AnalysisController {

  private final WrongItemAnalysisRepository repo;
  private final AnalysisService service;
  private final ProviderRouter router;
  private final JdbcTemplate jdbc;

  public AnalysisController(
      WrongItemAnalysisRepository repo,
      AnalysisService service,
      ProviderRouter router,
      JdbcTemplate jdbc) {
    this.repo = repo;
    this.service = service;
    this.router = router;
    this.jdbc = jdbc;
  }

  @GetMapping("/{itemId}")
  public ResponseEntity<?> latest(@PathVariable Long itemId) {
    return repo.findTopByWrongItemIdOrderByVersionDesc(itemId)
        .<ResponseEntity<?>>map(a -> ResponseEntity.ok(vo(a)))
        .orElseGet(() -> ResponseEntity.status(404).body(Map.of("error", "not_analyzed")));
  }

  @PostMapping("/{itemId}/retry")
  public ResponseEntity<?> retry(@PathVariable Long itemId) {
    int v = service.analyze(itemId, true);
    if (v < 0) {
      return ResponseEntity.status(404).body(Map.of("error", "wrong_item_not_found"));
    }
    return ResponseEntity.accepted().body(Map.of("version", v));
  }

  @GetMapping("/provider")
  public Map<String, String> provider() {
    return Map.of("provider", router.currentProviderName());
  }

  @GetMapping("/{itemId}/similar")
  public ResponseEntity<?> similar(
      @PathVariable Long itemId, @RequestParam(defaultValue = "3") int k) {
    int capped = Math.min(Math.max(k, 1), 10);
    List<Map<String, Object>> rows =
        jdbc.queryForList(
            """
            SELECT id, (embedding <=> (SELECT embedding FROM wrong_item WHERE id = ?)) AS distance
            FROM wrong_item
            WHERE embedding IS NOT NULL
              AND deleted_at IS NULL
              AND id <> ?
            ORDER BY embedding <=> (SELECT embedding FROM wrong_item WHERE id = ?)
            LIMIT ?
            """,
            itemId, itemId, itemId, capped);
    return ResponseEntity.ok(Map.of("items", rows));
  }

  private Map<String, Object> vo(WrongItemAnalysis a) {
    return Map.of(
        "itemId", a.getWrongItemId(),
        "version", a.getVersion(),
        "provider", a.getModelProvider(),
        "model", a.getModelName(),
        "status", a.getStatus(),
        "explain", a.getErrorReason() == null ? "" : a.getErrorReason(),
        "causeTag", a.getErrorType() == null ? "" : a.getErrorType(),
        "autoTags", a.getKnowledgePoints() == null ? "[]" : a.getKnowledgePoints(),
        "finishedAt", a.getFinishedAt());
  }
}
