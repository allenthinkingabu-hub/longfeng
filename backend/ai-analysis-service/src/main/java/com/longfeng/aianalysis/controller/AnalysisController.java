package com.longfeng.aianalysis.controller;

import com.longfeng.aianalysis.llm.ProviderRouter;
import com.longfeng.aianalysis.service.AnalysisService;
import com.longfeng.aianalysis.service.dto.AnalysisVO;
import com.longfeng.aianalysis.service.dto.ExplainChunk;
import com.longfeng.aianalysis.service.dto.SimilarItemVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "analysis", description = "AI 错题分析 · S4 域端点")
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

  @Operation(summary = "获取最新分析结果", description = "返回 wrong_item 的最新一版 AnalysisVO · 404 when none")
  @ApiResponse(responseCode = "200", description = "分析结果 AnalysisVO")
  @ApiResponse(responseCode = "404", description = "尚无分析记录")
  @GetMapping(value = "/{itemId}", produces = MediaType.APPLICATION_JSON_VALUE)
  public AnalysisVO latest(@Parameter(description = "wrong_item ID") @PathVariable Long itemId) {
    return service
        .findLatest(itemId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "analysis not found"));
  }

  @Operation(summary = "相似题召回", description = "pgvector cosine 召回 · distance ≤ 1.5 · 返回 { items: SimilarItem[] }")
  @ApiResponse(responseCode = "200", description = "相似题列表 SimilarResponse")
  @GetMapping(value = "/{itemId}/similar", produces = MediaType.APPLICATION_JSON_VALUE)
  public Map<String, List<SimilarItemVO>> similar(
      @Parameter(description = "wrong_item ID") @PathVariable Long itemId,
      @Parameter(description = "召回数量 · default 3 · max 10") @RequestParam(defaultValue = "3") int k) {
    List<SimilarItemVO> items = service.findSimilar(itemId, k);
    return Map.of("items", items);
  }

  @Operation(summary = "流式解析回放 (SSE)", description = "text/event-stream · 回放已存 explain 文本 · 终帧 done:true · 无分析时返回单帧 notice")
  @ApiResponse(responseCode = "200", description = "SSE 事件流 · ExplainChunk 序列")
  @GetMapping(value = "/{itemId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter stream(@Parameter(description = "wrong_item ID") @PathVariable Long itemId) {
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

  @Operation(summary = "管理员重试分析", description = "admin only · 需 X-Admin: true header · 返回 202 Accepted")
  @ApiResponse(responseCode = "202", description = "重试已接受")
  @ApiResponse(responseCode = "403", description = "非管理员")
  @ApiResponse(responseCode = "404", description = "wrong_item 不存在")
  @PostMapping("/{itemId}/retry")
  public ResponseEntity<Void> retry(
      @Parameter(description = "wrong_item ID") @PathVariable Long itemId,
      @Parameter(description = "管理员标识 · 必须为 'true'") @RequestHeader(value = "X-Admin", required = false) String admin) {
    if (!"true".equalsIgnoreCase(admin)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "admin only");
    }
    int result = service.retry(itemId);
    if (result == -1) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "wrong-item not found");
    }
    return ResponseEntity.accepted().build();
  }

  @Operation(summary = "当前 LLM 提供方", description = "反查 ProviderRouter 当前激活的 feature flag · 返回 { provider: 'dashscope' | 'openai' | 'stub' }")
  @ApiResponse(responseCode = "200", description = "{ provider: string }")
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
