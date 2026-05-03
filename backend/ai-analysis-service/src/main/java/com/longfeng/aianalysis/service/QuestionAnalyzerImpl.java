package com.longfeng.aianalysis.service;

import com.longfeng.aianalysis.llm.AnalysisResult;
import com.longfeng.aianalysis.llm.ChatClientFactory;
import com.longfeng.aianalysis.pii.FaceMaskingService;
import com.longfeng.aianalysis.pii.ImageNsfwDetector;
import com.longfeng.aianalysis.pii.PromptInjectionGuardAdvisor;
import com.longfeng.aianalysis.stub.ChatClient;
import com.longfeng.aianalysis.support.FallbackOrchestrator;
import com.longfeng.aianalysis.support.TempFileSpooler;
import com.longfeng.common.dto.AnalysisChunk;
import com.longfeng.common.exception.BusinessException;
import com.longfeng.common.exception.ErrCode;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * <p>C-14 修复：
 * <ul>
 *   <li>使用自定义 {@link com.longfeng.aianalysis.stub.ChatClient} 替代 Spring AI ChatClient
 *   <li>stub 实现返回确定性 placeholder AnalysisResult · 不调真 LLM（S9 QA b 轨可跑）
 *   <li>删除 Spring AI {@code FileSystemResource} / {@code MimeType} / {@code .prompt().advisors()} 调用链
 * </ul>
 *
 * <p>C7 红线（最重要 · plan §5.S3）：
 *
 * <ul>
 *   <li>byte[] 多模态图片**绝不**在 heap 持久化
 *   <li>{@link Resource} → {@link TempFileSpooler#spool} 立即落盘 → 传 path 给 ChatClient
 *   <li>finally 块**必释放** spool 文件
 *   <li>本类**无任何 byte[] field / static 缓存**
 * </ul>
 *
 * <p>核心调用链（stub 版）：
 *
 * <pre>
 *   spool(image) → NSFW 过滤 → face mask → injectionGuard.guard(prompt)
 *      → chatClient.analyze(prompt, spoolFilePath, subject)  ← stub 返回 placeholder
 *   → 终态 chunk DONE → finally release()
 * </pre>
 *
 * <p>异常路径：任一 provider fail → {@link FallbackOrchestrator#tryWithFallback} 三段降级；
 * 全断 → manual placeholder + {@code AnalysisChunk.fail("ai.fallback.manual")}。
 */
@Service
public class QuestionAnalyzerImpl implements QuestionAnalyzer {

  private static final Logger LOG = LoggerFactory.getLogger(QuestionAnalyzerImpl.class);

  /** D-AI timeout-sync · 同步 8s 红线（TDD §10.2）。 */
  private static final Duration SYNC_TIMEOUT = Duration.ofSeconds(8);

  /** D-AI timeout-stream · 流式 15s 红线（TDD §10.2）。 */
  private static final Duration STREAM_TIMEOUT = Duration.ofSeconds(15);

  private final ChatClientFactory chatClientFactory;
  private final TempFileSpooler spooler;
  private final ImageNsfwDetector nsfwDetector;
  private final FaceMaskingService faceMasking;
  private final PromptInjectionGuardAdvisor injectionGuard;
  private final FallbackOrchestrator fallbackOrchestrator;
  private final AnalysisStreamHub streamHub;

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
      @Value("classpath:/prompts/wrong-question-analysis.st") Resource promptTplResource) {
    this.chatClientFactory = chatClientFactory;
    this.spooler = spooler;
    this.nsfwDetector = nsfwDetector;
    this.faceMasking = faceMasking;
    this.injectionGuard = injectionGuard;
    this.fallbackOrchestrator = fallbackOrchestrator;
    this.streamHub = streamHub;
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

    Mono<AnalysisResult> producer =
        Mono.fromCallable(
                () -> {
                  // 1. C7：spool · 立即释放 byte[]
                  Path tmp = spooler.spool(taskId, image);
                  spoolRef.set(tmp);
                  sink.tryEmitNext(AnalysisChunk.ocr());

                  // 2. PII 过滤
                  if (!nsfwDetector.isSafe(tmp)) {
                    sink.tryEmitNext(AnalysisChunk.fail("ai.nsfw.blocked"));
                    throw new BusinessException(
                        ErrCode.VALIDATION_FAILED, "msgkey:common.error.validation_failed");
                  }
                  Path masked = faceMasking.mask(tmp);
                  sink.tryEmitNext(AnalysisChunk.analysis("LLM 推理中"));

                  // 3. ChatClient 调用 · 走 fallback 三段
                  return doAnalyze(taskId, image, subject, masked);
                })
            .subscribeOn(Schedulers.boundedElastic())
            .timeout(STREAM_TIMEOUT)
            .doOnNext(
                result -> {
                  sink.tryEmitNext(AnalysisChunk.steps(Map.of("draft", true)));
                  sink.tryEmitNext(AnalysisChunk.done(result));
                })
            .doOnError(
                ex -> {
                  LOG.warn("streamAnalyze failed · taskId={} · cause={}", taskId, ex.getMessage());
                  sink.tryEmitNext(AnalysisChunk.fail("ai.error.provider_unavailable"));
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
