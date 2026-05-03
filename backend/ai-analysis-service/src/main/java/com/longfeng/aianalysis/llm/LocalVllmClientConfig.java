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
 * 本地 vLLM 备份供应商配置 · D-AI-Tier-Policy 中 VIP_PLUS 私有化部署用 / 全外网失联兜底。
 *
 * <p>激活条件：{@code longfeng.ai.provider=local-vllm}。
 *
 * <p>实现策略：vLLM serving 默认提供 OpenAI 兼容端点
 * ({@code http://localhost:8000/v1})，复用 {@link OpenAiChatModel}。
 *
 * <p>典型用法：staging 环境无外网时切到本地，或 D-AI-Tier-Policy VIP_PLUS 用户启用 BGE-LLaVA。
 */
@Configuration
@ConditionalOnProperty(name = "longfeng.ai.provider", havingValue = "local-vllm")
public class LocalVllmClientConfig {

  @Bean
  ChatClient localVllmChatClient(
      @Value("${longfeng.ai.local-vllm.api-key:not-needed}") String apiKey,
      @Value("${longfeng.ai.local-vllm.base-url:http://localhost:8000/v1}") String baseUrl,
      @Value("${longfeng.ai.local-vllm.model:bge-llava}") String model,
      @Value("${longfeng.ai.local-vllm.temperature:0.2}") double temperature) {

    OpenAiApi api = new OpenAiApi(baseUrl, apiKey);
    OpenAiChatOptions options =
        OpenAiChatOptions.builder().withModel(model).withTemperature((float) temperature).build();
    OpenAiChatModel chatModel = new OpenAiChatModel(api, options);
    return ChatClient.builder(chatModel).build();
  }
}
