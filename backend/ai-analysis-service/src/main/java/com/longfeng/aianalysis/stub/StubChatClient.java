package com.longfeng.aianalysis.stub;

import com.longfeng.aianalysis.llm.AnalysisResult;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stub {@link ChatClient} 实现 · C-14 修复核心 · b 模式不调真 LLM。
 *
 * <p>返回确定性 placeholder {@link AnalysisResult}，满足：
 * <ul>
 *   <li>S9 QA Agent b 轨 e2e 可跑（不依赖外部 API）
 *   <li>前端 P03 "AI 分析中"流水线 4 阶段动画可走完
 *   <li>FallbackOrchestrator 三段降级链路可测
 * </ul>
 *
 * <p>A 轨真 LLM：参见 {@link ChatClient} 接口 Javadoc · 换一个 okhttp 实现 bean 即可。
 */
public class StubChatClient implements ChatClient {

  private static final Logger LOG = LoggerFactory.getLogger(StubChatClient.class);

  private final String provider;

  public StubChatClient(String provider) {
    this.provider = provider;
  }

  @Override
  public AnalysisResult analyze(String prompt, String imageBase64OrPath, String subject) {
    LOG.info(
        "[stub] ChatClient.analyze called · provider={} subject={} · returning placeholder",
        provider,
        subject);
    // Stub：返回确定性占位结果 · 不调真 LLM
    return new AnalysisResult(
        /* stem            */ "（stub OCR）示例题干：求方程 x²-5x+6=0 的解",
        /* subject         */ subject != null ? subject : "MATH",
        /* knowledgePoints */ List.of("二次方程", "因式分解"),
        /* errorType       */ "CONCEPT",
        /* errorReason     */ "（stub）AI 分析占位：学生混淆了韦达定理的应用场景",
        /* solutionSteps   */ List.of(
            "（stub）步骤 1：分解因式 (x-2)(x-3)=0",
            "（stub）步骤 2：x₁=2, x₂=3"),
        /* difficulty      */ 2,
        /* variants        */ List.of("（stub）变式：x²-7x+12=0"));
  }

  @Override
  public String providerName() {
    return provider;
  }
}
