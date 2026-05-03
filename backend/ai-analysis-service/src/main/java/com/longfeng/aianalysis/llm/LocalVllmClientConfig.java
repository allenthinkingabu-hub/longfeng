package com.longfeng.aianalysis.llm;

import com.longfeng.aianalysis.stub.ChatClient;
import com.longfeng.aianalysis.stub.StubChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 本地 vLLM 备份供应商配置 · D-AI-Tier-Policy 中 VIP_PLUS 私有化部署用 / 全外网失联兜底。
 *
 * <p>激活条件：{@code longfeng.ai.provider=local-vllm}。
 *
 * <p>C-14 修复：删除 Spring AI 1.0.0-M1 依赖 · 返回 {@link StubChatClient}（stub impl）。
 * vLLM serving 默认提供 OpenAI 兼容端点，A 轨实现只需换 okhttp 封装。
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
      @Value("${longfeng.ai.local-vllm.model:bge-llava}") String model) {
    // C-14 stub: 不调真 LLM · 返回确定性 placeholder
    // TODO(A 轨): 用 okhttp 封装真实 vLLM 调用 · apiKey + baseUrl + model 已绑定
    return new StubChatClient("local-vllm");
  }
}
