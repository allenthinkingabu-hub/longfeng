package com.longfeng.aianalysis.llm;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

/**
 * Test stub of {@link ChatModel} · returns canned JSON for {@link AnalysisResult} schema.
 *
 * <p>F-02/F-05/F-07 教训应用：纯 inline class · 不用 Mockito mock。
 * 避免触碰真实 LLM API · 测试速度 < 10ms。
 */
final class StubChatModel implements ChatModel {

  static final StubChatModel INSTANCE = new StubChatModel();

  private static final String CANNED_JSON =
      """
      {
        "stem": "求方程 x²-5x+6=0 的解",
        "subject": "MATH",
        "knowledgePoints": ["二次函数·根的判别式"],
        "errorType": "CONCEPT",
        "errorReason": "未运用韦达定理",
        "solutionSteps": ["a=1, b=-5, c=6", "Δ=b²-4ac=1>0", "x=(5±1)/2 → x₁=3, x₂=2"],
        "difficulty": 2,
        "variants": ["求 x²-7x+12=0", "求 x²+3x-4=0"]
      }
      """;

  private StubChatModel() {}

  @Override
  public ChatResponse call(Prompt prompt) {
    AssistantMessage msg = new AssistantMessage(CANNED_JSON);
    Generation gen = new Generation(msg, ChatGenerationMetadata.NULL);
    return new ChatResponse(java.util.List.of(gen), ChatResponseMetadata.builder().build());
  }

  @Override
  public Flux<ChatResponse> stream(Prompt prompt) {
    return Flux.just(call(prompt));
  }
}
