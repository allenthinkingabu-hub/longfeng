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
 * OpenAI / Azure OpenAI 供应商配置 · D-AI-Provider-Default 海外档默认（gpt-4o-mini）。
 *
 * <p>激活条件：{@code longfeng.ai.provider=openai}（@ConditionalOnProperty 强互斥 · plan §5.S3）。
 *
 * <p>同一 Spring Context 仅当本配置激活时才注入 {@link ChatClient} bean ——
 * {@link ChatClientFactory} 通过 {@code ObjectProvider<ChatClient>} 拣到这个唯一实例。
 *
 * <p>真实供应商凭证从 {@code spring.ai.openai.api-key} / {@code base-url} 读（Spring AI starter 标准位置）。
 */
@Configuration
@ConditionalOnProperty(name = "longfeng.ai.provider", havingValue = "openai")
public class OpenAiClientConfig {

  @Bean
  ChatClient openAiChatClient(
      @Value("${spring.ai.openai.api-key:sk-test}") String apiKey,
      @Value("${spring.ai.openai.base-url:https://api.openai.com}") String baseUrl,
      @Value("${spring.ai.openai.chat.options.model:gpt-4o-mini}") String model,
      @Value("${spring.ai.openai.chat.options.temperature:0.2}") double temperature) {

    OpenAiApi api = new OpenAiApi(baseUrl, apiKey);
    OpenAiChatOptions options =
        OpenAiChatOptions.builder().withModel(model).withTemperature((float) temperature).build();
    OpenAiChatModel chatModel = new OpenAiChatModel(api, options);
    return ChatClient.builder(chatModel).build();
  }
}
