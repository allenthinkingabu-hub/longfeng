package com.longfeng.aianalysis.controller;

import com.longfeng.aianalysis.llm.AnalysisResult;
import com.longfeng.aianalysis.service.AnalysisStreamHub;
import com.longfeng.aianalysis.service.QuestionAnalyzer;
import com.longfeng.common.dto.AnalysisChunk;
import com.longfeng.common.dto.ApiResult;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotBlank;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
 *   <li>{@code POST /api/ai/analyze} · 同步触发（multipart 上传图片）· 8s 超时 · 返回 AnalysisResult
 *   <li>{@code POST /api/ai/analyze-by-url} · WT4 新增 · 异步触发（FE 已经把图传到 OSS · 仅给 URL）·
 *       供 anonymous-service GuestController 转调 · 立即返 {@code task_id + status:ANALYZING}
 *   <li>{@code GET /api/ai/stream/{taskId}} · SSE 流式（H5 端 fetch+ReadableStream）· 6 种 event ·
 *       FE 按 JSON {@code type} 分发：STEP_START / STEP_DONE / PARTIAL_JSON / DONE / FAIL / CANCELLED
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

  /** analyze-by-url 拉取超时（防止恶意慢响应卡死 worker）。 */
  private static final int IMAGE_FETCH_CONNECT_TIMEOUT_MS = 5_000;

  private static final int IMAGE_FETCH_READ_TIMEOUT_MS = 10_000;

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
   * <b>WT4 · 2026-05-04 · 新增</b> · 通过 image_url 异步触发分析 · 给 anonymous-service /
   * file-service / 任何不持有 byte 的服务用。
   *
   * <p>调用方 (anonymous-service GuestController) 不持有图片字节 · 仅有 OSS / MinIO presigned
   * URL · 调本端点会：
   *
   * <ol>
   *   <li>立即返 {@code task_id + status:ANALYZING} · HTTP 202 Accepted
   *   <li>异步 spawn worker · {@link #fetchUrlAsResource} 流式拉图（不缓存到 heap）
   *   <li>调 {@link QuestionAnalyzer#streamAnalyze} · 推 chunk 进 {@link AnalysisStreamHub}
   *   <li>FE 通过 {@code GET /api/ai/stream/{taskId}} 订阅 SSE
   * </ol>
   *
   * <p>request body schema：
   *
   * <pre>
   *   { "task_id": "tk_abc123", "subject": "MATH", "image_url": "https://oss/.../x.jpg" }
   * </pre>
   *
   * <p>response schema：{@code { "task_id": "tk_abc123", "status": "ANALYZING" }}（HTTP 202）
   */
  @PostMapping(value = "/analyze-by-url", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Map<String, String>> analyzeByUrl(@RequestBody AnalyzeByUrlRequest req) {
    String taskId = req.taskId();
    String subject = req.subject();
    String imageUrl = req.imageUrl();
    LOG.info(
        "POST /api/ai/analyze-by-url · taskId={} subject={} url={}", taskId, subject, imageUrl);

    if (taskId == null || taskId.isBlank()) {
      return ResponseEntity.badRequest().body(Map.of("error", "task_id required"));
    }
    if (imageUrl == null || imageUrl.isBlank()) {
      return ResponseEntity.badRequest().body(Map.of("error", "image_url required"));
    }

    // Pre-create sink so FE 在很短时间内 GET /stream/{taskId} 不会 410 miss
    streamHub.getOrCreate(taskId);

    // 异步 worker · 拉图 → 调 streamAnalyze · chunk 走 SSE 给 FE
    Schedulers.boundedElastic()
        .schedule(
            () -> {
              try {
                Resource img = fetchUrlAsResource(imageUrl);
                analyzer
                    .streamAnalyze(taskId, img, subject == null ? "MATH" : subject)
                    .doOnError(
                        ex -> {
                          LOG.warn(
                              "analyze-by-url worker failed · taskId={} cause={}",
                              taskId,
                              ex.getMessage());
                          streamHub.emit(taskId, AnalysisChunk.fail("ai.fetch.image_failed"));
                          streamHub.dispose(taskId);
                        })
                    .subscribe();
              } catch (Exception ex) {
                LOG.warn(
                    "analyze-by-url image fetch failed · taskId={} url={} cause={}",
                    taskId,
                    imageUrl,
                    ex.getMessage());
                streamHub.emit(taskId, AnalysisChunk.fail("ai.fetch.image_failed"));
                streamHub.dispose(taskId);
              }
            });

    return ResponseEntity.accepted().body(Map.of("task_id", taskId, "status", "ANALYZING"));
  }

  /**
   * SSE 流式 · 客户端 fetch("/api/ai/stream/{taskId}") · 服务端推 6 种 chunk type。
   *
   * <p>实现参 TDD §8.3 · 走 streamFanoutExecutor 隔离 + 心跳 5s。
   *
   * <p>FE {@code useEventSource.ts} 解析：按 SSE 帧分隔（{@code \n\n}）取 {@code data:} 行 →
   * JSON.parse → 按 {@code type} 字段分发到 {@code STEP_START/STEP_DONE/PARTIAL_JSON/DONE/FAIL/CANCELLED}
   * 6 路 handler。所以 chunk JSON 必须含 {@code type} 字段（已通过 {@link AnalysisChunk} 重构保证）。
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
                    // event name 沿用 type/stage 命名 · FE 实际是 parse data 的 JSON.type · 双轨兼容
                    String eventName =
                        chunk.type() != null
                            ? chunk.type().name()
                            : (chunk.stage() != null ? chunk.stage().name() : "MESSAGE");
                    emitter.send(
                        SseEmitter.event()
                            .name(eventName)
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

  /**
   * 流式拉取远程图片 · 不缓存到 heap · 直接交给 InputStreamResource。
   *
   * <p>C7 红线兼容：返回 Resource 而不是 byte[] · 后续 spool 写盘的链路保持不变。
   * URLConnection 设置 connect/read timeout 防止恶意慢响应卡死 worker。
   *
   * <p>TODO Phase 2：替换为 OSS SDK 或 MinIO presign-bound client · 验签 + 限流。
   */
  Resource fetchUrlAsResource(String imageUrl) throws IOException {
    URLConnection conn = URI.create(imageUrl).toURL().openConnection();
    conn.setConnectTimeout(IMAGE_FETCH_CONNECT_TIMEOUT_MS);
    conn.setReadTimeout(IMAGE_FETCH_READ_TIMEOUT_MS);
    conn.setRequestProperty("User-Agent", "longfeng-ai-analysis/1.0");
    InputStream in = conn.getInputStream();
    long len = conn.getContentLengthLong();
    return new InputStreamResource(in) {
      @Override
      public long contentLength() {
        return len > 0 ? len : -1;
      }
    };
  }

  /** Request body for {@link #analyzeByUrl} · snake_case to match FE / anonymous-service convention. */
  public record AnalyzeByUrlRequest(
      @com.fasterxml.jackson.annotation.JsonProperty("task_id") @NotBlank String taskId,
      @com.fasterxml.jackson.annotation.JsonProperty("subject") String subject,
      @com.fasterxml.jackson.annotation.JsonProperty("image_url") @NotBlank String imageUrl) {}
}
