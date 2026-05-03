package com.longfeng.aianalysis.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.longfeng.aianalysis.llm.AnalysisResult;
import com.longfeng.common.dto.AnalysisChunk;
import com.longfeng.common.exception.BusinessException;
import com.longfeng.common.exception.ErrCode;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Sinks;

/**
 * Plan §5.S3 出口门禁：FallbackOrchestrator 主→备→手填三段降级 PASS。
 */
class FallbackOrchestratorTest {

  @Test
  void primaryProvider_succeeds_returnsResult() {
    FallbackOrchestrator orch = new FallbackOrchestrator("qianwen,openai,zhipu");
    AnalysisResult dummy =
        new AnalysisResult("stem", "MATH", List.of(), "OTHER", "ok", List.of(), 1, List.of());

    AnalysisResult got =
        orch.tryWithFallback("qianwen", provider -> dummy, /* sink */ null);

    assertThat(got).isSameAs(dummy);
  }

  @Test
  void primaryFails_secondaryProviderSucceeds_returnsSecondaryResult() {
    FallbackOrchestrator orch = new FallbackOrchestrator("qianwen,openai,zhipu");
    AnalysisResult okResult =
        new AnalysisResult("ok", "MATH", List.of(), "OTHER", "ok", List.of(), 1, List.of());

    AtomicInteger callCount = new AtomicInteger(0);
    AnalysisResult got =
        orch.tryWithFallback(
            "qianwen",
            provider -> {
              int c = callCount.incrementAndGet();
              if (c == 1) {
                throw new RuntimeException("primary down");
              }
              return okResult;
            },
            null);

    assertThat(got).isSameAs(okResult);
    assertThat(callCount.get()).isEqualTo(2);
  }

  @Test
  void allProvidersFail_returnsManualPlaceholder() {
    FallbackOrchestrator orch = new FallbackOrchestrator("qianwen,openai,zhipu");

    AnalysisResult got =
        orch.tryWithFallback(
            "qianwen",
            provider -> {
              throw new RuntimeException("all-down");
            },
            null);

    // 手填 placeholder · errorReason 包含"AI 暂不可用"
    assertThat(got.errorReason()).contains("AI 暂不可用");
    assertThat(got.errorType()).isEqualTo("OTHER");
  }

  @Test
  void allProvidersFail_emitsFailChunkToSink() {
    FallbackOrchestrator orch = new FallbackOrchestrator("qianwen,openai");
    Sinks.Many<AnalysisChunk> sink = Sinks.many().multicast().onBackpressureBuffer();
    List<AnalysisChunk> received = new ArrayList<>();
    sink.asFlux().subscribe(received::add);

    orch.tryWithFallback(
        "qianwen",
        provider -> {
          throw new RuntimeException("down");
        },
        sink);

    assertThat(received)
        .filteredOn(c -> c.stage() == AnalysisChunk.Stage.FAIL)
        .extracting(AnalysisChunk::chunk)
        .contains("ai.fallback.manual");
  }

  @Test
  void fallbackHit_emitsAnalysisChunkNoticeToSink() {
    FallbackOrchestrator orch = new FallbackOrchestrator("qianwen,openai");
    AnalysisResult ok =
        new AnalysisResult("ok", "MATH", List.of(), "OTHER", "ok", List.of(), 1, List.of());
    Sinks.Many<AnalysisChunk> sink = Sinks.many().multicast().onBackpressureBuffer();
    List<AnalysisChunk> received = new ArrayList<>();
    sink.asFlux().subscribe(received::add);

    orch.tryWithFallback(
        "qianwen",
        provider -> {
          if ("qianwen".equals(provider)) {
            throw new RuntimeException("primary down");
          }
          return ok;
        },
        sink);

    assertThat(received)
        .anySatisfy(
            c ->
                assertThat(c.chunk())
                    .as("fallback notice should mention provider")
                    .contains("已切换到备用模型"));
  }

  @Test
  void emptyChain_throwsAiProviderUnavailable() {
    FallbackOrchestrator orch = new FallbackOrchestrator("");
    assertThatThrownBy(orch::requireChainNonEmpty)
        .isInstanceOf(BusinessException.class)
        .matches(ex -> ((BusinessException) ex).errCode() == ErrCode.AI_PROVIDER_UNAVAILABLE);
  }

  @Test
  void fallbackChain_orderPreserved() {
    FallbackOrchestrator orch = new FallbackOrchestrator("qianwen,openai,zhipu,local-vllm");
    assertThat(orch.fallbackChain()).containsExactly("qianwen", "openai", "zhipu", "local-vllm");
  }
}
