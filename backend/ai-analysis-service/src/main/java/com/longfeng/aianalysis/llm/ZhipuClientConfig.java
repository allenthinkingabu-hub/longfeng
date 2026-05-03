package com.longfeng.aianalysis.llm;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 智谱 GLM-4V Plus 供应商配置 · D-AI-Provider-Default 国内备份。
 *
 * <p>激活条件：{@code longfeng.ai.provider=zhipu}。
 *
 * <p>实现策略：智谱 BigModel 平台提供 OpenAI 兼容端点
 * ({@code https://open.bigmodel.cn/api/paas/v4})，复用 Spring AI 的 {@link OpenAiChatModel}。
 *
 * <p>独立 namespace {@code longfeng.ai.zhipu.*} · 凭证密钥隔离 · 切换 provider 时无 key 漂移风险。
 */
@Configuration
@ConditionalOnProperty(name = "longfeng.ai.provider", havingValue = "zhipu")
public class ZhipuClientConfig {

  @Bean
  ChatClient zhipuChatClient(
      @Value("${longfeng.ai.zhipu.api-key:sk-zhipu-test}") String apiKey,
      @Value("${longfeng.ai.zhipu.base-url:https://open.bigmodel.cn/api/paas/v4}") String baseUrl,
      @Value("${longfeng.ai.zhipu.model:glm-4v-plus}") String model,
      @Value("${longfeng.ai.zhipu.temperature:0.2}") double temperature) {

    OpenAiApi api = new OpenAiApi(baseUrl, apiKey);
    OpenAiChatOptions options =
        OpenAiChatOptions.builder().withModel(model).withTemperature((float) temperature).build();
    OpenAiChatModel chatModel = new OpenAiChatModel(api, options);
    return ChatClient.builder(chatModel).build();
  }
}
