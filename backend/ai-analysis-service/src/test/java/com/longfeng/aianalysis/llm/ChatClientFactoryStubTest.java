package com.longfeng.aianalysis.llm;

import static org.assertj.core.api.Assertions.assertThat;

import com.longfeng.aianalysis.stub.ChatClient;
import com.longfeng.aianalysis.stub.StubChatClient;
import org.junit.jupiter.api.Test;

/**
 * 出口门禁：4 provider stub bean 正确返回 {@link ChatClient} 实现。
 *
 * <p>验证 4 个 *ClientConfig 的 stub bean 方法返回正确的 provider 名称，
 * 不依赖 Spring Context（纯 POJO 实例化）。
 *
 * <p>F-02/F-05 教训：不用 Mockito · 不需要 Spring ApplicationContext。
 */
class ChatClientFactoryStubTest {

  @Test
  void openAiClientConfig_returnsStubWithCorrectProvider() {
    OpenAiClientConfig config = new OpenAiClientConfig();
    ChatClient client = config.openAiChatClient("sk-test", "https://api.openai.com", "gpt-4o-mini");

    assertThat(client).isInstanceOf(StubChatClient.class);
    assertThat(client.providerName()).isEqualTo("openai");
  }

  @Test
  void qianwenClientConfig_returnsStubWithCorrectProvider() {
    QianwenClientConfig config = new QianwenClientConfig();
    // BUG-LF-19 · placeholder apiKey 仍走 stub 兜底路径 · ObjectMapper 占位即可
    ChatClient client = config.qianwenChatClient(
        "sk-qwen-test",
        "https://dashscope.aliyuncs.com/compatible-mode/v1",
        "qwen-vl-max",
        new com.fasterxml.jackson.databind.ObjectMapper());

    assertThat(client).isInstanceOf(StubChatClient.class);
    assertThat(client.providerName()).isEqualTo("qianwen");
  }

  @Test
  void qianwenClientConfig_realApiKey_returnsDashscopeChatClient() {
    QianwenClientConfig config = new QianwenClientConfig();
    // BUG-LF-19 · 非 placeholder apiKey 应实例化 DashscopeChatClient (真 HTTP impl)
    ChatClient client = config.qianwenChatClient(
        "sk-real-prod-key",
        "https://dashscope.aliyuncs.com/compatible-mode/v1",
        "qwen-vl-max",
        new com.fasterxml.jackson.databind.ObjectMapper());

    assertThat(client).isInstanceOf(DashscopeChatClient.class);
    assertThat(client.providerName()).isEqualTo("qianwen");
  }

  @Test
  void zhipuClientConfig_returnsStubWithCorrectProvider() {
    ZhipuClientConfig config = new ZhipuClientConfig();
    ChatClient client = config.zhipuChatClient(
        "sk-zhipu-test",
        "https://open.bigmodel.cn/api/paas/v4",
        "glm-4v-plus");

    assertThat(client).isInstanceOf(StubChatClient.class);
    assertThat(client.providerName()).isEqualTo("zhipu");
  }

  @Test
  void localVllmClientConfig_returnsStubWithCorrectProvider() {
    LocalVllmClientConfig config = new LocalVllmClientConfig();
    ChatClient client = config.localVllmChatClient(
        "not-needed",
        "http://localhost:8000/v1",
        "bge-llava");

    assertThat(client).isInstanceOf(StubChatClient.class);
    assertThat(client.providerName()).isEqualTo("local-vllm");
  }

  @Test
  void allProviders_analyzeReturnsNonNullResult() {
    // 验证 stub analyze 返回有效 ChatResponse · usage=zero (stub 路径)
    ChatClient qianwen = new StubChatClient("qianwen");
    var response = qianwen.analyze("test prompt", "/tmp/test.jpg", "MATH");

    assertThat(response).isNotNull();
    assertThat(response.result()).isNotNull();
    assertThat(response.result().subject()).isEqualTo("MATH");
    assertThat(response.result().errorType()).isNotBlank();
    assertThat(response.result().solutionSteps()).isNotEmpty();
    assertThat(response.usage().isZero()).as("stub usage must be zero · 触发上游 length 估算回退").isTrue();
  }
}
