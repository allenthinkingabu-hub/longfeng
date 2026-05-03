package com.longfeng.aianalysis.support;

import com.longfeng.aianalysis.llm.AnalysisResult;
import com.longfeng.common.dto.AnalysisChunk;
import com.longfeng.common.exception.BusinessException;
import com.longfeng.common.exception.ErrCode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Sinks;

/**
 * D-AI Provider cross-fallback 三段降级编排（TDD §0.9 D-AI + plan §5.S3）。
 *
 * <p>降级链（按 {@code longfeng.ai.fallback-chain} 配置）：
 *
 * <ol>
 *   <li><strong>主</strong>：当前 {@code longfeng.ai.provider}（默认 qianwen）
 *   <li><strong>备</strong>：fallback chain 第二档（默认 openai）
 *   <li><strong>手填</strong>：链路全断 → 发布 {@code ai.fallback.manual} 事件 · 返回占位 AnalysisResult
 *       让前端走 P03 "请手动填写错因" 兜底（PRD §6 + TDD §6.4）
 * </ol>
 *
 * <p>调用方传入"实际 LLM 调用闭包"——本类只负责 try-each-provider 顺序编排 + 单一职责。
 * "已经付费给供应商的 token 不退"（D-AI-Cancel）—— fallback 触发不影响计费。
 */
@Component
public class FallbackOrchestrator {

  private static final Logger LOG = LoggerFactory.getLogger(FallbackOrchestrator.class);

  /** Fallback 顺序 · 来自 {@code longfeng.ai.fallback-chain}。 */
  private final List<String> fallbackChain;

  public FallbackOrchestrator(
      @Value("${longfeng.ai.fallback-chain:qianwen,openai,zhipu}") String fallbackChain) {
    this.fallbackChain =
        Arrays.stream(fallbackChain.split(",")).map(String::trim).filter(s -> !s.isBlank()).toList();
  }

  /**
   * 按 chain 顺序逐个尝试 LLM 调用 · 全部失败时降级到手填（返回 placeholder result）。
   *
   * @param activeProvider 当前激活 provider（用于跳过 chain 中重复项 / 日志）
   * @param invoker        provider 名 → AnalysisResult 闭包（具体 LLM 调用 + entity 解析逻辑）
   * @param sink           SSE / WS 出口 sink · 可空（同步调用场景）
   * @return 成功的 AnalysisResult · 全失败返回手填 placeholder
   */
  public AnalysisResult tryWithFallback(
      String activeProvider,
      Function<String, AnalysisResult> invoker,
      Sinks.Many<AnalysisChunk> sink) {
    List<Throwable> errors = new ArrayList<>();
    for (String provider : fallbackChain) {
      try {
        AnalysisResult result = invoker.apply(provider);
        if (!provider.equals(activeProvider)) {
          // fallback 命中 · 通知前端
          if (sink != null) {
            sink.tryEmitNext(AnalysisChunk.analysis("已切换到备用模型 " + provider));
          }
          LOG.warn("fallback hit · activeProvider={} → fallbackProvider={}", activeProvider, provider);
        }
        return result;
      } catch (Exception ex) {
        LOG.warn(
            "provider {} failed · trying next in chain · cause={}",
            provider,
            ex.getMessage());
        errors.add(ex);
      }
    }
    // 链路全断 → 手填降级
    LOG.error(
        "all providers in chain failed · chainSize={} · 进入手填降级 · errors={}",
        fallbackChain.size(),
        errors.stream().map(Throwable::getMessage).toList());
    if (sink != null) {
      sink.tryEmitNext(AnalysisChunk.fail("ai.fallback.manual"));
    }
    return manualFallbackPlaceholder();
  }

  /**
   * 手填占位 · 让前端 P03 显示"请手动填写错因"。
   *
   * <p>注意：返回的不是 throw · 因为业务侧需要"链路降级但流程不断"——前端拿到 placeholder 后
   * 弹出手填表单 · 学生 fill 后另发 {@code POST /api/wb/questions/{qid}:save} 写错因。
   */
  AnalysisResult manualFallbackPlaceholder() {
    return new AnalysisResult(
        "",
        null,
        List.of(),
        "OTHER",
        "AI 暂不可用，请手动填写错因",
        List.of(),
        null,
        List.of());
  }

  /** Throw if chain empty (sanity-check 配置错配时的快速失败)。 */
  void requireChainNonEmpty() {
    if (fallbackChain.isEmpty()) {
      throw new BusinessException(
          ErrCode.AI_PROVIDER_UNAVAILABLE, "msgkey:ai.error.provider_unavailable");
    }
  }

  /** 暴露给测试 · 检查 chain 配置。 */
  public List<String> fallbackChain() {
    return fallbackChain;
  }
}
