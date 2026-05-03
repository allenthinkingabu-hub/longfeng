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
 * 通义千问 VL（DashScope · qwen-vl-max）供应商配置 — D-AI-Provider-Default 国内默认。
 *
 * <p>激活条件：{@code longfeng.ai.provider=qianwen}（系统默认 · 国内合规）。
 *
 * <p>实现策略：阿里云 DashScope 提供 OpenAI 兼容端点
 * ({@code https://dashscope.aliyuncs.com/compatible-mode/v1})，因此复用 Spring AI 的
 * {@link OpenAiChatModel} —— 业务代码完全统一，仅 base-url + model 名称不同。
 *
 * <p>真实凭证从 {@code longfeng.ai.qianwen.api-key} / {@code base-url} 读
 * （独立命名空间，避免与海外档 OpenAI key 混淆）。
 */
@Configuration
@ConditionalOnProperty(name = "longfeng.ai.provider", havingValue = "qianwen", matchIfMissing = true)
public class QianwenClientConfig {

  @Bean
  ChatClient qianwenChatClient(
      @Value("${longfeng.ai.qianwen.api-key:sk-qwen-test}") String apiKey,
      @Value("${longfeng.ai.qianwen.base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}")
          String baseUrl,
      @Value("${longfeng.ai.qianwen.model:qwen-vl-max}") String model,
      @Value("${longfeng.ai.qianwen.temperature:0.2}") double temperature) {

    OpenAiApi api = new OpenAiApi(baseUrl, apiKey);
    OpenAiChatOptions options =
        OpenAiChatOptions.builder().withModel(model).withTemperature((float) temperature).build();
    OpenAiChatModel chatModel = new OpenAiChatModel(api, options);
    return ChatClient.builder(chatModel).build();
  }
}
