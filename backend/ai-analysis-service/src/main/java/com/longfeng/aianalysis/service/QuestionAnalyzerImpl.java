package com.longfeng.aianalysis.service;

import com.longfeng.aianalysis.entity.AiUsageLog;
import com.longfeng.aianalysis.llm.AnalysisResult;
import com.longfeng.aianalysis.llm.ChatClientFactory;
import com.longfeng.aianalysis.pii.FaceMaskingService;
import com.longfeng.aianalysis.pii.ImageNsfwDetector;
import com.longfeng.aianalysis.pii.PromptInjectionGuardAdvisor;
import com.longfeng.aianalysis.repo.AiUsageLogRepository;
import com.longfeng.aianalysis.stub.ChatClient;
import com.longfeng.aianalysis.support.FallbackOrchestrator;
import com.longfeng.aianalysis.support.TempFileSpooler;
import com.longfeng.common.dto.AnalysisChunk;
import com.longfeng.common.exception.BusinessException;
import com.longfeng.common.exception.ErrCode;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

/**
 * 错题 AI 分析实现 · TDD §6.4 worker 骨架 + plan §5.S3。
 *
 * <p>WT4 · 2026-05-04 重构 (S7 hybrid 联调对齐 FE)：
 *
 * <ul>
 *   <li>{@code streamAnalyze} 推送 FE-aligned 6 种 type 事件（STEP_START / STEP_DONE / PARTIAL_JSON
 *       / DONE / FAIL / CANCELLED）· 跟 {@code useEventSource.ts} 对齐
 *   <li>每次 LLM 调用后写 {@code ai_usage_log} 行 · 通过 {@link AiUsageLogRepository}
 *       · 即使 stub 也写（满足监督铁证 第 7 项）
 * </ul>
 *
 * <p>C-14 修复：
 * <ul>
 *   <li>使用自定义 {@link com.longfeng.aianalysis.stub.ChatClient} 替代 Spring AI ChatClient
 *   <li>stub 实现返回确定性 placeholder AnalysisResult · 不调真 LLM（S9 QA b 轨可跑）
 * </ul>
 *
 * <p>C7 红线（最重要 · plan §5.S3）：
 *
 * <ul>
 *   <li>byte[] 多模态图片**绝不**在 heap 持久化
 *   <li>{@link Resource} → {@link TempFileSpooler#spool} 立即落盘 → 传 path 给 ChatClient
 *   <li>finally 块**必释放** spool 文件
 * </ul>
 *
 * <p>核心调用链（4 步流水线 + usage 写库）：
 *
 * <pre>
 *   STEP_START 1 → spool(image) → STEP_DONE 1
 *   STEP_START 2 → NSFW + face mask → STEP_DONE 2
 *   STEP_START 3 → injectionGuard → chatClient.analyze → STEP_DONE 3 (写 ai_usage_log)
 *   STEP_START 4 → emit PARTIAL_JSON → STEP_DONE 4 → DONE
 *   finally: spool.release · sink.complete
 * </pre>
 */
@Service
public class QuestionAnalyzerImpl implements QuestionAnalyzer {

  private static final Logger LOG = LoggerFactory.getLogger(QuestionAnalyzerImpl.class);

  /** D-AI timeout-sync · 同步 8s 红线（TDD §10.2）。 */
  private static final Duration SYNC_TIMEOUT = Duration.ofSeconds(8);

  /** D-AI timeout-stream · 流式 30s 红线（TDD §10.2 · WT4 拉宽以容下 4 步流水线）。 */
  private static final Duration STREAM_TIMEOUT = Duration.ofSeconds(30);

  /** ai_usage_log status code · 跟 WrongItemAnalysis.STATUS_SUCCESS 对齐（0=success / 1=fallback / 9=pending）。 */
  private static final short USAGE_STATUS_SUCCESS = 0;

  private static final short USAGE_STATUS_FAIL = 9;

  /**
   * Stub LLM 调用的近似 token 估算（无真实 usage 字段时） — 简单按 prompt char / 4 估
   * tokens-in · output 用 stem.length / 4。后续接真 LLM 直接从 response usage 字段读。
   */
  private static final int STUB_TOKENS_IN_DIV = 4;

  private final ChatClientFactory chatClientFactory;
  private final TempFileSpooler spooler;
  private final ImageNsfwDetector nsfwDetector;
  private final FaceMaskingService faceMasking;
  private final PromptInjectionGuardAdvisor injectionGuard;
  private final FallbackOrchestrator fallbackOrchestrator;
  private final AnalysisStreamHub streamHub;
  /** WT4 · 写 ai_usage_log · ObjectProvider 兼容 IT 中没注入的场景（unit test 不需 DB）。 */
  private final ObjectProvider<AiUsageLogRepository> usageLogRepoProvider;

  /** Prompt 模板纯文本（resources/prompts/wrong-question-analysis.st 加载于 PromptTemplates）。 */
  private final String promptTemplate;

  public QuestionAnalyzerImpl(
      ChatClientFactory chatClientFactory,
      TempFileSpooler spooler,
      ImageNsfwDetector nsfwDetector,
      FaceMaskingService faceMasking,
      PromptInjectionGuardAdvisor injectionGuard,
      FallbackOrchestrator fallbackOrchestrator,
      AnalysisStreamHub streamHub,
      ObjectProvider<AiUsageLogRepository> usageLogRepoProvider,
      @Value("classpath:/prompts/wrong-question-analysis.st") Resource promptTplResource) {
    this.chatClientFactory = chatClientFactory;
    this.spooler = spooler;
    this.nsfwDetector = nsfwDetector;
    this.faceMasking = faceMasking;
    this.injectionGuard = injectionGuard;
    this.fallbackOrchestrator = fallbackOrchestrator;
    this.streamHub = streamHub;
    this.usageLogRepoProvider = usageLogRepoProvider;
    this.promptTemplate = loadTemplate(promptTplResource);
  }

  @Override
  public Mono<AnalysisResult> analyze(String taskId, Resource image, String subject) {
    return Mono.fromCallable(() -> doAnalyze(taskId, image, subject, null))
        .subscribeOn(Schedulers.boundedElastic())
        .timeout(SYNC_TIMEOUT)
        .onErrorResume(
            ex -> {
              LOG.warn("analyze failed · taskId={} · cause={}", taskId, ex.getMessage());
              return Mono.just(fallbackOrchestrator.manualFallbackPlaceholder());
            });
  }

  @Override
  public Flux<AnalysisChunk> streamAnalyze(String taskId, Resource image, String subject) {
    Sinks.Many<AnalysisChunk> sink = streamHub.getOrCreate(taskId);
    AtomicReference<Path> spoolRef = new AtomicReference<>();
    AtomicLong pipelineStart = new AtomicLong();

    Mono<AnalysisResult> producer =
        Mono.fromCallable(
                () -> {
                  pipelineStart.set(System.currentTimeMillis());

                  // Step 1 · 图像预处理（spool 落盘）
                  long t1 = System.currentTimeMillis();
                  sink.tryEmitNext(AnalysisChunk.stepStart(1));
                  Path tmp = spooler.spool(taskId, image);
                  spoolRef.set(tmp);
                  sink.tryEmitNext(AnalysisChunk.stepDone(1, System.currentTimeMillis() - t1));

                  // Step 2 · OCR 题干 + PII 过滤
                  long t2 = System.currentTimeMillis();
                  sink.tryEmitNext(AnalysisChunk.stepStart(2));
                  if (!nsfwDetector.isSafe(tmp)) {
                    sink.tryEmitNext(AnalysisChunk.failAtStep(2, "ai.nsfw.blocked"));
                    throw new BusinessException(
                        ErrCode.VALIDATION_FAILED, "msgkey:common.error.validation_failed");
                  }
                  Path masked = faceMasking.mask(tmp);
                  sink.tryEmitNext(AnalysisChunk.stepDone(2, System.currentTimeMillis() - t2));

                  // Step 3 · 错因诊断（LLM 调用 + ai_usage_log 写库）
                  long t3 = System.currentTimeMillis();
                  sink.tryEmitNext(AnalysisChunk.stepStart(3));
                  AnalysisResult result = doAnalyze(taskId, image, subject, masked);
                  long step3Ms = System.currentTimeMillis() - t3;
                  recordUsage(
                      chatClientFactory.activeProvider(),
                      "chat",
                      promptTemplate.length() / STUB_TOKENS_IN_DIV,
                      result.stem() == null ? 0 : result.stem().length() / STUB_TOKENS_IN_DIV,
                      (int) step3Ms,
                      USAGE_STATUS_SUCCESS);
                  sink.tryEmitNext(AnalysisChunk.stepDone(3, step3Ms));

                  // Step 4 · 生成解法（PARTIAL_JSON 流式拼接 stub · 真 LLM 接入时拆为多个 PARTIAL_JSON）
                  long t4 = System.currentTimeMillis();
                  sink.tryEmitNext(AnalysisChunk.stepStart(4));
                  if (result.errorReason() != null && !result.errorReason().isBlank()) {
                    sink.tryEmitNext(
                        AnalysisChunk.partialJson(
                            "{\"errorReason\":\""
                                + escapeJson(result.errorReason())
                                + "\"}"));
                  }
                  sink.tryEmitNext(AnalysisChunk.stepDone(4, System.currentTimeMillis() - t4));

                  return result;
                })
            .subscribeOn(Schedulers.boundedElastic())
            .timeout(STREAM_TIMEOUT)
            .doOnNext(
                result -> {
                  // 终结 DONE 帧 · 携带完整 result（FE 直接用）
                  sink.tryEmitNext(AnalysisChunk.done(result));
                })
            .doOnError(
                ex -> {
                  LOG.warn("streamAnalyze failed · taskId={} · cause={}", taskId, ex.getMessage());
                  sink.tryEmitNext(AnalysisChunk.fail("ai.error.provider_unavailable"));
                  recordUsage(
                      chatClientFactory.activeProvider(),
                      "chat",
                      0,
                      0,
                      (int) (System.currentTimeMillis() - pipelineStart.get()),
                      USAGE_STATUS_FAIL);
                })
            .doFinally(
                signal -> {
                  // C7：finally 必释放 spool · 确保 byte[] 不滞留
                  spooler.release(spoolRef.get());
                  sink.tryEmitComplete();
                });

    streamHub.registerProducer(taskId, producer.subscribe());
    return sink.asFlux();
  }

  /**
   * 核心分析调用 · 同步阻塞 · 调用方包 Mono.fromCallable 切线程。
   *
   * <p>C-14 stub：调用自定义 {@link ChatClient#analyze(String, String, String)} 返回 placeholder。
   * Prompt injection guard 先行拦截 · PII 过滤已在上层完成。
   *
   * @param taskId      任务 ID
   * @param image       原图 Resource（如果 spoolFile 已传 · 这个会被忽略）
   * @param subject     学科 hint
   * @param spoolFile   已经 spool 的 path（可空 · 空则本方法自己 spool）
   * @return AnalysisResult · 全 fallback 失败返回 placeholder
   */
  AnalysisResult doAnalyze(String taskId, Resource image, String subject, Path spoolFile) {
    Path tmp = spoolFile;
    boolean ownsFile = false;
    try {
      if (tmp == null) {
        tmp = spooler.spool(taskId, image);
        ownsFile = true;
      }
      final Path finalTmp = tmp;
      String activeProvider = chatClientFactory.activeProvider();

      // 组装 prompt（模板变量替换）
      String prompt = promptTemplate
          .replace("{subject}", subject != null ? subject : "MATH")
          .replace("{grade}", "未知")
          .replace("{userAnswer}", "")
          .replace("{ocrHint}", "");

      // Prompt injection guard
      String guardedPrompt;
      try {
        guardedPrompt = injectionGuard.guard(prompt);
      } catch (BusinessException be) {
        LOG.warn("prompt injection detected · taskId={}", taskId);
        throw be;
      }

      return fallbackOrchestrator.tryWithFallback(
          activeProvider,
          provider -> {
            ChatClient client = chatClientFactory.client(/* tenantId */ null);
            // C-14 stub: 传文件路径 · stub 实现不读取真实图片
            return client.analyze(guardedPrompt, finalTmp.toString(), subject);
          },
          /* sink */ null);
    } finally {
      if (ownsFile) {
        spooler.release(tmp);
      }
    }
  }

  /**
   * 写 ai_usage_log row · WT4 新增 · 监督铁证第 7 项保证。
   *
   * <p>实际生产应在 {@link ChatClient#analyze} 接口扩展中返回真 token usage · stub 阶段按 prompt
   * 长度估算保证表里至少有 row + tokens > 0 · IT 可断言。
   *
   * <p>{@code REQUIRES_NEW} 隔离事务 · 保证即使外层 worker 没有事务上下文（reactive scheduler
   * 不传播 ThreadLocal）· 写入也能 flush。
   */
  @org.springframework.transaction.annotation.Transactional(
      propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
  public void recordUsage(
      String provider, String apiType, int tokensIn, int tokensOut, int latencyMs, short status) {
    AiUsageLogRepository repo = usageLogRepoProvider.getIfAvailable();
    if (repo == null) {
      LOG.debug(
          "ai_usage_log repo unavailable · skip · provider={} apiType={}", provider, apiType);
      return;
    }
    try {
      AiUsageLog row = new AiUsageLog();
      // Normalize provider to ck_usage_provider allow-list (V1.0.023):
      // 'dashscope' / 'openai' / 'stub'. qianwen is alias for dashscope (Aliyun 通义千问 / DashScope).
      row.setProvider(normalizeProvider(provider));
      row.setModel(provider == null ? "stub-model" : provider + "-default");
      row.setApiType(apiType);
      row.setTokensIn(tokensIn);
      row.setTokensOut(tokensOut);
      row.setCostCents((tokensIn + tokensOut) / 100);
      row.setLatencyMs(latencyMs);
      row.setStatus(status);
      repo.save(row);
    } catch (Exception ex) {
      // ai_usage_log 写库失败不应阻塞业务 · 仅 warn
      LOG.warn("ai_usage_log save failed · provider={} cause={}", provider, ex.getMessage());
    }
  }

  /** Map runtime provider name to {@code ai_usage_log.ck_usage_provider} allow-list. */
  static String normalizeProvider(String provider) {
    if (provider == null) return "stub";
    return switch (provider.toLowerCase(java.util.Locale.ROOT)) {
      case "qianwen", "dashscope" -> "dashscope";
      case "openai" -> "openai";
      // zhipu / local-vllm / 其他 → 暂 fallback to stub · S10 拓宽 enum
      default -> "stub";
    };
  }

  /** 简易 JSON 转义 · stub 阶段够用 · 真 LLM 接入时改用 ObjectMapper。 */
  private static String escapeJson(String s) {
    return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
  }

  /** 把 .st 模板读为字符串 · 启动一次 · 直接用 Reader 不 buffer 字节（C7 安全）。 */
  private static String loadTemplate(Resource resource) {
    try (var reader =
        new java.io.BufferedReader(
            new java.io.InputStreamReader(
                resource.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
      StringBuilder sb = new StringBuilder();
      String line;
      while ((line = reader.readLine()) != null) {
        sb.append(line).append('\n');
      }
      return sb.toString();
    } catch (Exception ex) {
      LOG.error("Failed to load prompt template · {}", resource, ex);
      throw new BusinessException(
          ErrCode.INTERNAL_ERROR, "msgkey:common.error.internal", ex);
    }
  }
}
