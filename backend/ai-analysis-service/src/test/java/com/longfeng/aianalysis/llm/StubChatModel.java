package com.longfeng.aianalysis.llm;

import com.longfeng.aianalysis.stub.ChatClient;
import java.util.List;

/**
 * Test stub of {@link ChatClient} · returns canned {@link AnalysisResult} for unit tests.
 *
 * <p>C-14 修复：删除 Spring AI {@code ChatModel} 依赖 · 改为实现自定义 {@link ChatClient} 接口。
 * F-02/F-05/F-07 教训应用：纯 inline class · 不用 Mockito mock。
 * 避免触碰真实 LLM API · 测试速度 &lt; 10ms。
 *
 * <p>BUG-LF-19 fix：返回 {@link ChatResponse} · usage 用 {@link Usage#zero()} 触发 length 估算回退。
 */
public final class StubChatModel implements ChatClient {

  public static final StubChatModel INSTANCE = new StubChatModel();

  private StubChatModel() {}

  @Override
  public ChatResponse analyze(String prompt, String imageBase64OrPath, String subject) {
    AnalysisResult result =
        new AnalysisResult(
            /* stem            */ "求方程 x²-5x+6=0 的解",
            /* subject         */ "MATH",
            /* knowledgePoints */ List.of("二次函数·根的判别式"),
            /* errorType       */ "CONCEPT",
            /* errorReason     */ "未运用韦达定理",
            /* solutionSteps   */ List.of(
                "a=1, b=-5, c=6",
                "Δ=b²-4ac=1>0",
                "x=(5±1)/2 → x₁=3, x₂=2"),
            /* difficulty      */ 2,
            /* variants        */ List.of("求 x²-7x+12=0", "求 x²+3x-4=0"));
    return new ChatResponse(result, Usage.zero());
  }

  @Override
  public String providerName() {
    return "stub-test";
  }
}
