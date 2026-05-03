package com.longfeng.aianalysis.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.longfeng.aianalysis.stub.ChatClient;
import com.longfeng.common.exception.BusinessException;
import com.longfeng.common.exception.ErrCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Unit tests for {@link ChatClientFactory} · plan §5.S3 出口门禁：4 provider 启动 + hot-swap。
 *
 * <p>C-14 修复：使用自定义 {@link ChatClient} 接口替代 Spring AI ChatClient。
 * 策略：用 inline subclass stub 模拟 {@link ObjectProvider}，避免 Mockito mock 非接口类
 * （F-02 / F-05 教训）。
 */
class ChatClientFactoryTest {

  @Test
  void supportedProviders_returnsAll4() {
    ChatClientFactory factory =
        new ChatClientFactory(stubProvider(/* present */ null), "qianwen");
    assertThat(factory.supportedProviders())
        .containsKeys("openai", "qianwen", "zhipu", "local-vllm");
  }

  @Test
  void client_returnsAvailableClient() {
    ChatClient stubClient = StubChatModel.INSTANCE;
    ChatClientFactory factory = new ChatClientFactory(stubProvider(stubClient), "openai");
    assertThat(factory.client("tenant-A")).isSameAs(stubClient);
    assertThat(factory.activeProvider()).isEqualTo("openai");
  }

  @Test
  void client_throwsAiProviderUnavailable_whenNoChatClientBeanActivated() {
    ChatClientFactory factory =
        new ChatClientFactory(stubProvider(/* present */ null), "qianwen");
    assertThatThrownBy(() -> factory.client("tenant-A"))
        .isInstanceOf(BusinessException.class)
        .matches(
            ex -> ((BusinessException) ex).errCode() == ErrCode.AI_PROVIDER_UNAVAILABLE,
            "errCode AI_PROVIDER_UNAVAILABLE");
  }

  @Test
  void hotSwap_byChangingActiveProvider_isReflected() {
    // 模拟 provider 切换 · 不同 activeProvider 注入
    ChatClient first = StubChatModel.INSTANCE;
    ChatClient second = new com.longfeng.aianalysis.stub.StubChatClient("openai");

    ChatClientFactory factory1 = new ChatClientFactory(stubProvider(first), "qianwen");
    ChatClientFactory factory2 = new ChatClientFactory(stubProvider(second), "openai");

    assertThat(factory1.activeProvider()).isEqualTo("qianwen");
    assertThat(factory2.activeProvider()).isEqualTo("openai");
    assertThat(factory1.client(null)).isNotSameAs(factory2.client(null));
  }

  /** Inline subclass stub of ObjectProvider · F-02 教训：避免 Mockito mock 非接口类。 */
  private static ObjectProvider<ChatClient> stubProvider(ChatClient impl) {
    return new ObjectProvider<>() {
      @Override
      public ChatClient getObject() {
        return impl;
      }

      @Override
      public ChatClient getObject(Object... args) {
        return impl;
      }

      @Override
      public ChatClient getIfAvailable() {
        return impl;
      }

      @Override
      public ChatClient getIfUnique() {
        return impl;
      }
    };
  }
}
