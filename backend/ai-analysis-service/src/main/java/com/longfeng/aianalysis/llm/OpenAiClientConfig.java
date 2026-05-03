package com.longfeng.aianalysis.llm;

import com.longfeng.aianalysis.stub.ChatClient;
import com.longfeng.aianalysis.stub.StubChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAI / Azure OpenAI 供应商配置 · D-AI-Provider-Default 海外档默认（gpt-4o-mini）。
 *
 * <p>激活条件：{@code longfeng.ai.provider=openai}（@ConditionalOnProperty 强互斥 · plan §5.S3）。
 *
 * <p>C-14 修复：删除 Spring AI 1.0.0-M1 依赖 · 返回 {@link StubChatClient}（stub impl）。
 * A 轨真 LLM 留 user 后续接真 API key + okhttp/WebClient 封装 {@link ChatClient} 接口。
 *
 * <p>真实供应商凭证保留 @Value 绑定 · 供未来 A 轨实现使用 · 当前仅记录 providerName。
 */
@Configuration
@ConditionalOnProperty(name = "longfeng.ai.provider", havingValue = "openai")
public class OpenAiClientConfig {

  @Bean
  ChatClient openAiChatClient(
      @Value("${spring.ai.openai.api-key:sk-test}") String apiKey,
      @Value("${spring.ai.openai.base-url:https://api.openai.com}") String baseUrl,
      @Value("${spring.ai.openai.chat.options.model:gpt-4o-mini}") String model) {
    // C-14 stub: 不调真 LLM · 返回确定性 placeholder
    // TODO(A 轨): 用 okhttp 封装真实 OpenAI 调用 · apiKey + baseUrl + model 已绑定
    return new StubChatClient("openai");
  }
}
