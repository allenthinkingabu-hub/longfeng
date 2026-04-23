package com.longfeng.aianalysis.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.longfeng.aianalysis.llm.LlmProvider;
import com.longfeng.aianalysis.llm.StubProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Default Provider wiring. IT + local dev without API keys → StubProvider pair. Real transports
 * (dashscope/openai) bind under the {@code llm-real} profile or when {@code AI_API_KEY} is set,
 * left out here to avoid accidental spend.
 */
@Configuration
public class LlmConfig {

  @Bean
  @Primary
  @ConditionalOnMissingBean
  public ObjectMapper objectMapper() {
    ObjectMapper om = new ObjectMapper();
    om.registerModule(new JavaTimeModule());
    om.configure(
        com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    return om;
  }

  @Bean
  public LlmProvider dashscopeProvider(
      @Value("${spring.ai.dashscope.chat.options.model:qwen-plus}") String chatModel) {
    return new StubProvider("dashscope", chatModel);
  }

  @Bean
  public LlmProvider openaiProvider(
      @Value("${spring.ai.openai.chat.options.model:gpt-4o-mini}") String chatModel) {
    return new StubProvider("openai", chatModel);
  }
}
