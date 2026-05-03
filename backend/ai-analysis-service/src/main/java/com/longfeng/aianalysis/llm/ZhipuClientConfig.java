package com.longfeng.aianalysis.llm;

import com.longfeng.aianalysis.stub.ChatClient;
import com.longfeng.aianalysis.stub.StubChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 智谱 GLM-4V Plus 供应商配置 · D-AI-Provider-Default 国内备份。
 *
 * <p>激活条件：{@code longfeng.ai.provider=zhipu}。
 *
 * <p>C-14 修复：删除 Spring AI 1.0.0-M1 依赖 · 返回 {@link StubChatClient}（stub impl）。
 * 智谱 BigModel 平台提供 OpenAI 兼容端点，A 轨实现只需换 okhttp 封装。
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
      @Value("${longfeng.ai.zhipu.model:glm-4v-plus}") String model) {
    // C-14 stub: 不调真 LLM · 返回确定性 placeholder
    // TODO(A 轨): 用 okhttp 封装真实 GLM-4V 调用 · apiKey + baseUrl + model 已绑定
    return new StubChatClient("zhipu");
  }
}
