package com.longfeng.aianalysis.llm;

import com.longfeng.aianalysis.stub.ChatClient;
import com.longfeng.aianalysis.stub.StubChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 通义千问 VL（DashScope · qwen-vl-max）供应商配置 — D-AI-Provider-Default 国内默认。
 *
 * <p>激活条件：{@code longfeng.ai.provider=qianwen}（系统默认 · matchIfMissing=true · 国内合规）。
 *
 * <p>C-14 修复：删除 Spring AI 1.0.0-M1 依赖 · 返回 {@link StubChatClient}（stub impl）。
 * 阿里云 DashScope 提供 OpenAI 兼容端点，A 轨实现只需换 okhttp 封装 + 本配置注入真实凭证。
 *
 * <p>真实凭证从 {@code longfeng.ai.qianwen.api-key} / {@code base-url} 读（独立命名空间）。
 */
@Configuration
@ConditionalOnProperty(name = "longfeng.ai.provider", havingValue = "qianwen", matchIfMissing = true)
public class QianwenClientConfig {

  @Bean
  ChatClient qianwenChatClient(
      @Value("${longfeng.ai.qianwen.api-key:sk-qwen-test}") String apiKey,
      @Value("${longfeng.ai.qianwen.base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}")
          String baseUrl,
      @Value("${longfeng.ai.qianwen.model:qwen-vl-max}") String model) {
    // C-14 stub: 不调真 LLM · 返回确定性 placeholder
    // TODO(A 轨): 用 okhttp 封装真实 DashScope 调用 · apiKey + baseUrl + model 已绑定
    return new StubChatClient("qianwen");
  }
}
