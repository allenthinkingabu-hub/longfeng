package com.longfeng.aianalysis.controller;

import com.longfeng.aianalysis.llm.AnalysisResult;
import com.longfeng.aianalysis.service.AnalysisStreamHub;
import com.longfeng.aianalysis.service.QuestionAnalyzer;
import com.longfeng.common.dto.AnalysisChunk;
import com.longfeng.common.dto.ApiResult;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

/**
 * AI 分析端点（同步 POST + 流式 GET SSE）· TDD §12.2 + §8.3 + plan §5.S3。
 *
 * <p>双端点：
 *
 * <ul>
 *   <li>{@code POST /api/ai/analyze} · 同步触发 · 8s 超时（D-AI timeout-sync）· 返回 AnalysisResult
 *   <li>{@code GET /api/ai/stream/{taskId}} · SSE 流式（H5 端 EventSource）· 4 stage chunk · 60s 总超时
 * </ul>
 *
 * <p>D-SSE 必备 HTTP 头（TDD §8.6）：{@code X-Accel-Buffering: no} / {@code Cache-Control: no-store}
 * / {@code Connection: keep-alive}。
 *
 * <p>异常路径：
 *
 * <ul>
 *   <li>taskId 不存在 → SSE 410 Gone（前端切 polling fallback `GET /api/ai/result/{taskId}`）
 *   <li>fallback 全断 → emit AnalysisChunk.fail("ai.fallback.manual") · 前端弹手填表单
 * </ul>
 */
@RestController
@RequestMapping("/api/ai")
public class AnalyzeController {

  private static final Logger LOG = LoggerFactory.getLogger(AnalyzeController.class);

  /** SSE 总生命周期超时（D-SSE）。 */
  private static final long SSE_TOTAL_TIMEOUT_MS = 60_000L;

  /** 单 chunk 间隔超时（D-SSE）。 */
  private static final Duration SSE_CHUNK_TIMEOUT = Duration.ofSeconds(15);

  private final QuestionAnalyzer analyzer;
  private final AnalysisStreamHub streamHub;

  public AnalyzeController(QuestionAnalyzer analyzer, AnalysisStreamHub streamHub) {
    this.analyzer = analyzer;
    this.streamHub = streamHub;
  }

  /**
   * 同步分析 · multipart 上传图片 + 学科 + taskId · 返回 AnalysisResult。
   *
   * <p>注意：C7 红线 · MultipartFile 通过 InputStreamResource 包装为 Resource 立即流给
   * QuestionAnalyzer · controller 层不持有 byte[]。
   */
  @PostMapping(value = "/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ApiResult<AnalysisResult> analyze(
      @RequestParam("taskId") String taskId,
      @RequestParam("subject") String subject,
      @RequestParam("image") MultipartFile imageFile)
      throws IOException {
    LOG.info("POST /api/ai/analyze · taskId={} subject={}", taskId, subject);
    Resource imageResource = new InputStreamResource(imageFile.getInputStream());
    AnalysisResult result =
        analyzer.analyze(taskId, imageResource, subject).block(Duration.ofSeconds(10));
    return ApiResult.ok(result);
  }

  /**
   * SSE 流式 · 客户端 EventSource("/api/ai/stream/{taskId}") · 服务端按 4 stage 推 chunk。
   *
   * <p>实现参 TDD §8.3 · 走 streamFanoutExecutor 隔离 + 心跳 5s。
   */
  @GetMapping(value = "/stream/{taskId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter stream(@PathVariable String taskId, HttpServletResponse resp) {
    // D-SSE · 必备 HTTP 头（TDD §8.6）
    resp.setHeader("X-Accel-Buffering", "no");
    resp.setHeader("Cache-Control", "no-store");
    resp.setHeader("Connection", "keep-alive");
    resp.setHeader("Content-Type", "text/event-stream; charset=UTF-8");

    SseEmitter emitter = new SseEmitter(SSE_TOTAL_TIMEOUT_MS);
    Sinks.Many<AnalysisChunk> sink = streamHub.getOrCreate(taskId);

    Disposable subscription =
        sink.asFlux()
            .timeout(SSE_CHUNK_TIMEOUT)
            .doOnNext(
                chunk -> {
                  try {
                    emitter.send(
                        SseEmitter.event()
                            .name(chunk.stage().name())
                            .data(chunk, MediaType.APPLICATION_JSON));
                  } catch (IOException ioe) {
                    emitter.completeWithError(ioe);
                  }
                })
            .doOnComplete(emitter::complete)
            .doOnError(emitter::completeWithError)
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe();

    emitter.onCompletion(subscription::dispose);
    emitter.onTimeout(subscription::dispose);
    emitter.onError(t -> subscription.dispose());

    return emitter;
  }

  /**
   * Polling fallback · TDD §8.7 / §12.2.4 · 当 SSE / WS 都不可用时用。
   *
   * <p>当前 phase 简化实现：返回当前活跃 task 数（实际生产应查 wb_analysis_result 终态）。
   * Phase 2 接入数据库 / Redis 状态镜像。
   */
  @GetMapping("/result/{taskId}")
  public ApiResult<String> result(@PathVariable String taskId) {
    boolean active = streamHub.lookup(taskId).isPresent();
    return ApiResult.ok(active ? "ANALYZING" : "DONE");
  }
}
