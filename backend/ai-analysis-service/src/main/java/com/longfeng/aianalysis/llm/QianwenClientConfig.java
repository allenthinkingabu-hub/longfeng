package com.longfeng.aianalysis.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.longfeng.aianalysis.stub.ChatClient;
import com.longfeng.aianalysis.stub.StubChatClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 通义千问 VL（DashScope · qwen-vl-max）供应商配置 — D-AI-Provider-Default 国内默认。
 *
 * <p>激活条件：{@code longfeng.ai.provider=qianwen}（系统默认 · matchIfMissing=true · 国内合规）。
 *
 * <p>BUG-LF-19 fix：返回 {@link DashscopeChatClient}（真 HTTP 集成） · 仅当 apiKey 是 placeholder
 * （{@code sk-qwen-test} / 空值）时回退到 {@link StubChatClient} · 防 IT / unit test 跑挂。
 *
 * <p>真实凭证从 {@code longfeng.ai.qianwen.api-key} / {@code base-url} 读（独立命名空间）。
 */
@Configuration
@ConditionalOnProperty(name = "longfeng.ai.provider", havingValue = "qianwen", matchIfMissing = true)
public class QianwenClientConfig {

  private static final Logger LOG = LoggerFactory.getLogger(QianwenClientConfig.class);

  @Bean
  ChatClient qianwenChatClient(
      @Value("${longfeng.ai.qianwen.api-key:sk-qwen-test}") String apiKey,
      @Value("${longfeng.ai.qianwen.base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}")
          String baseUrl,
      @Value("${longfeng.ai.qianwen.model:qwen-vl-max}") String model,
      ObjectMapper om) {
    // BUG-LF-19 fix · 真 DashScope HTTP 集成
    // 仅当 key 是 placeholder 时回 stub · 防 IT/unit test 跑挂
    if (apiKey == null || apiKey.isBlank() || apiKey.startsWith("sk-qwen-test")) {
      LOG.info(
          "QianwenClientConfig · apiKey is placeholder · falling back to StubChatClient (b 轨/unit test 模式)");
      return new StubChatClient("qianwen");
    }
    LOG.info(
        "QianwenClientConfig · DashscopeChatClient activated · baseUrl={} model={}", baseUrl, model);
    return new DashscopeChatClient(apiKey, baseUrl, model, om);
  }
}
