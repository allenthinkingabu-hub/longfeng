package com.longfeng.aianalysis.controller;

import com.longfeng.aianalysis.llm.ProviderRouter;
import com.longfeng.aianalysis.service.AnalysisService;
import com.longfeng.aianalysis.service.dto.AnalysisVO;
import com.longfeng.aianalysis.service.dto.ExplainChunk;
import com.longfeng.aianalysis.service.dto.SimilarItemVO;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Analysis REST + SSE controller (Layer 4 · be-build-spec controller layer).
 *
 * <p>Endpoints (paths after gateway-stripped {@code /api/v1/} prefix):
 *
 * <ul>
 *   <li>{@code GET  /analysis/{itemId}}          · REST JSON · returns latest {@link AnalysisVO}
 *   <li>{@code GET  /analysis/{itemId}/similar}  · REST JSON · returns {@code { items: [] }}
 *   <li>{@code GET  /analysis/{itemId}/stream}   · SSE       · replays persisted explain chunks
 *   <li>{@code POST /analysis/{itemId}/retry}    · admin     · bumps to next version, 202 Accepted
 *   <li>{@code GET  /analysis/provider}          · observe   · current ProviderRouter selection
 * </ul>
 *
 * <p>This layer is a thin pass-through: no repository / jdbc / @Transactional · all business
 * logic stays in {@link AnalysisService}. {@link ResponseStatusException} is used inline for
 * 4xx mapping; {@link ProviderRouter.NoProviderException} maps to 503 via the local handler.
 */
@RestController
@RequestMapping("/analysis")
public class AnalysisController {

  private static final Logger LOG = LoggerFactory.getLogger(AnalysisController.class);

  private final AnalysisService service;
  private final ProviderRouter router;

  public AnalysisController(AnalysisService service, ProviderRouter router) {
    this.service = service;
    this.router = router;
  }

  /** GET /analysis/{itemId} · returns the latest persisted AnalysisVO · 404 when none. */
  @GetMapping(value = "/{itemId}", produces = MediaType.APPLICATION_JSON_VALUE)
  public AnalysisVO latest(@PathVariable Long itemId) {
    return service
        .findLatest(itemId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "analysis not found"));
  }

  /** GET /analysis/{itemId}/similar?k=3 · pgvector recall · service-layer clamps k & filters. */
  @GetMapping(value = "/{itemId}/similar", produces = MediaType.APPLICATION_JSON_VALUE)
  public Map<String, List<SimilarItemVO>> similar(
      @PathVariable Long itemId, @RequestParam(defaultValue = "3") int k) {
    List<SimilarItemVO> items = service.findSimilar(itemId, k);
    return Map.of("items", items);
  }

  /**
   * GET /analysis/{itemId}/stream · SSE replay of persisted explain text (BR-16).
   *
   * <p>Service guarantees a non-empty {@link ExplainChunk} list (worst case: a single terminal
   * notice frame), so the controller never needs a 4xx branch here — sse_protocol.behavior_rules
   * mandates "SSE 不应中途 4xx".
   */
  @GetMapping(value = "/{itemId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter stream(@PathVariable Long itemId) {
    SseEmitter emitter = new SseEmitter(0L); // 0 = no timeout · short replay never blocks
    try {
      List<ExplainChunk> chunks = service.streamExplain(itemId);
      for (ExplainChunk c : chunks) {
        emitter.send(SseEmitter.event().data(c, MediaType.APPLICATION_JSON));
      }
      emitter.complete();
    } catch (Exception ex) {
      LOG.warn("SSE stream failed for itemId={} · {}", itemId, ex.getMessage());
      emitter.completeWithError(ex);
    }
    return emitter;
  }

  /** POST /analysis/{itemId}/retry · admin gate via X-Admin header (G-03). */
  @PostMapping("/{itemId}/retry")
  public ResponseEntity<Void> retry(
      @PathVariable Long itemId,
      @RequestHeader(value = "X-Admin", required = false) String admin) {
    if (!"true".equalsIgnoreCase(admin)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "admin only");
    }
    int result = service.retry(itemId);
    if (result == -1) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "wrong-item not found");
    }
    return ResponseEntity.accepted().build();
  }

  /** GET /analysis/provider · reflects the active ProviderRouter selection. */
  @GetMapping(value = "/provider", produces = MediaType.APPLICATION_JSON_VALUE)
  public Map<String, String> provider() {
    return Map.of("provider", router.currentProviderName());
  }

  /** No LlmProvider bean wired → 503 Service Unavailable (key_rules · NoProviderException → 503). */
  @ExceptionHandler(ProviderRouter.NoProviderException.class)
  public ResponseEntity<Map<String, String>> handleNoProvider(ProviderRouter.NoProviderException ex) {
    LOG.warn("no LLM provider available · {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(Map.of("error", "no_provider", "message", ex.getMessage()));
  }
}
