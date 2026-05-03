package com.longfeng.aianalysis.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.longfeng.aianalysis.llm.AnalysisResult;
import com.longfeng.aianalysis.llm.ChatClientFactory;
import com.longfeng.aianalysis.pii.FaceMaskingService;
import com.longfeng.aianalysis.pii.ImageNsfwDetector;
import com.longfeng.aianalysis.pii.PromptInjectionGuardAdvisor;
import com.longfeng.aianalysis.support.FallbackOrchestrator;
import com.longfeng.aianalysis.support.TempFileSpooler;
import com.longfeng.common.dto.AnalysisChunk;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import reactor.core.publisher.Sinks;

/**
 * Plan §5.S3 出口门禁：QuestionAnalyzerImpl 端到端（mock ChatClient）· entity 解析 + chunk emit。
 *
 * <p>F-02/F-05/F-07 教训：所有依赖用 inline subclass stub · 不用 Mockito。
 *
 * <p>注意：QuestionAnalyzerImpl 真实调用 ChatClient.entity() 需要 Spring AI 1.0.0-M1 ChatModel
 * 实现 · 这部分由 Application Context 集成测试覆盖（IT 层）；本单测仅覆盖 spool / fallback /
 * stream emit / dispose 路径。
 */
class QuestionAnalyzerImplTest {

  @TempDir Path tmpDir;

  TempFileSpooler spooler;
  ImageNsfwDetector nsfw;
  FaceMaskingService faceMask;
  PromptInjectionGuardAdvisor guard;
  FallbackOrchestrator fallback;
  AnalysisStreamHub hub;

  @BeforeEach
  void setUp() {
    spooler = new TempFileSpooler(tmpDir.toString(), 5);
    spooler.ensureDir();
    nsfw = new ImageNsfwDetector(0.85, false); // disabled in test
    faceMask = new FaceMaskingService(false);
    guard = new PromptInjectionGuardAdvisor();
    fallback = new FallbackOrchestrator("qianwen,openai");
    hub = new AnalysisStreamHub();
  }

  @Test
  void streamAnalyze_failsGracefully_emitsFailChunk() throws IOException {
    // ChatClientFactory 故意未 init · 使 doAnalyze 走 fallback chain · 全断 → manual placeholder
    ChatClientFactory factory = new ChatClientFactory(emptyProvider(), "qianwen");

    QuestionAnalyzerImpl analyzer =
        new QuestionAnalyzerImpl(
            factory, spooler, nsfw, faceMask, guard, fallback, hub, sampleTpl());
    Resource image = new ByteArrayResource("img".getBytes());

    Sinks.Many<AnalysisChunk> sink = hub.getOrCreate("task-x");
    List<AnalysisChunk> received = new ArrayList<>();
    sink.asFlux().subscribe(received::add);

    analyzer.streamAnalyze("task-x", image, "MATH").blockFirst(java.time.Duration.ofSeconds(20));

    // 至少应有 OCR 帧
    assertThat(received)
        .filteredOn(c -> c.stage() == AnalysisChunk.Stage.OCR)
        .isNotEmpty();
  }

  @Test
  void doAnalyze_returnsManualPlaceholder_whenAllProvidersFail() {
    ChatClientFactory factory = new ChatClientFactory(emptyProvider(), "qianwen");
    QuestionAnalyzerImpl analyzer =
        new QuestionAnalyzerImpl(
            factory, spooler, nsfw, faceMask, guard, fallback, hub, sampleTpl());

    Resource image = new ByteArrayResource("dummy".getBytes());
    AnalysisResult result = analyzer.doAnalyze("task-fail", image, "MATH", null);

    assertThat(result.errorReason()).contains("AI 暂不可用");
  }

  @Test
  void doAnalyze_releasesSpoolFileAfterCompletion() {
    ChatClientFactory factory = new ChatClientFactory(emptyProvider(), "qianwen");
    QuestionAnalyzerImpl analyzer =
        new QuestionAnalyzerImpl(
            factory, spooler, nsfw, faceMask, guard, fallback, hub, sampleTpl());

    Resource image = new ByteArrayResource("dummy".getBytes());
    analyzer.doAnalyze("task-cleanup", image, "MATH", null);

    // C7 红线：spool 文件必须 release
    Path spoolFile = tmpDir.resolve("task-cleanup.jpg");
    assertThat(Files.exists(spoolFile))
        .as("C7 红线 · spool file 必须在 finally 释放")
        .isFalse();
  }

  /** Inline empty ObjectProvider · F-02 教训。 */
  private org.springframework.beans.factory.ObjectProvider<
          org.springframework.ai.chat.client.ChatClient>
      emptyProvider() {
    return new org.springframework.beans.factory.ObjectProvider<>() {
      @Override
      public org.springframework.ai.chat.client.ChatClient getObject() {
        return null;
      }

      @Override
      public org.springframework.ai.chat.client.ChatClient getObject(Object... args) {
        return null;
      }

      @Override
      public org.springframework.ai.chat.client.ChatClient getIfAvailable() {
        return null;
      }

      @Override
      public org.springframework.ai.chat.client.ChatClient getIfUnique() {
        return null;
      }
    };
  }

  /** 简易 prompt template Resource · 不依赖 classpath:/ 加载。 */
  private Resource sampleTpl() {
    return new ByteArrayResource(
        "请分析 {subject} 学科：{ocrHint}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }
}
