package com.longfeng.aianalysis.llm;

import com.longfeng.aianalysis.stub.ChatClient;
import com.longfeng.common.exception.BusinessException;
import com.longfeng.common.exception.ErrCode;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * D-AI Provider 多供应商工厂（TDD §0.7 + §0.9 D-AI + plan §5.S3）。
 *
 * <p>C-14 修复：使用自定义 {@link com.longfeng.aianalysis.stub.ChatClient} 接口替代
 * Spring AI 1.0.0-M1 {@code ChatClient}（M1 → 1.0 GA API 大改导致编译失败）。
 *
 * <p>"业务代码不感知供应商差异"——业务调 {@code factory.client(tenantId)} 拿一个 {@link ChatClient}，
 * 实际背后是哪家由 {@code longfeng.ai.provider} 配置项决定。
 *
 * <p>每家 provider 的 {@link ChatClient} bean 由对应的 {@code *ClientConfig} 类按
 * {@code @ConditionalOnProperty(name="longfeng.ai.provider", havingValue="...")} 隔离注入；
 * 同一时刻 Spring Context 内**只有一个**激活 ChatClient bean —— 切换 provider = 改 Nacos 配置 + reload。
 *
 * <p>多租户场景：tenantId 用作未来 per-tenant override 的钩子（当前 Phase 1 取系统默认）。
 *
 * <p>核心约束（plan §5.S3）：
 *
 * <ul>
 *   <li>4 provider config 必须用 {@code @ConditionalOnProperty} 互斥
 *   <li>找不到激活 client → throw {@link BusinessException}({@link ErrCode#AI_PROVIDER_UNAVAILABLE})
 * </ul>
 */
@Component
public class ChatClientFactory {

  private static final Logger LOG = LoggerFactory.getLogger(ChatClientFactory.class);

  /** 4 档 provider 各一个 Spring bean · 同一时刻只 ConditionalOnProperty 激活一个。 */
  private final ObjectProvider<ChatClient> chatClients;

  /** 当前生效 provider 名（来自 yaml · 用于日志 / fallback 决策）。 */
  private final String activeProvider;

  public ChatClientFactory(
      ObjectProvider<ChatClient> chatClients,
      @Value("${longfeng.ai.provider:qianwen}") String activeProvider) {
    this.chatClients = chatClients;
    this.activeProvider = activeProvider;
  }

  /**
   * 取一个可用 ChatClient · 对业务透明 · 错误情况转 {@link BusinessException}。
   *
   * @param tenantId 多租户 hint · Phase 1 仅用于日志，未来用于 per-tenant override（D-AI-Provider-Default）
   * @return 自定义 {@link ChatClient}（已注入对应 provider 的 stub impl）
   * @throws BusinessException {@link ErrCode#AI_PROVIDER_UNAVAILABLE} 若 4 档全未激活
   */
  public ChatClient client(String tenantId) {
    ChatClient client = chatClients.getIfAvailable();
    if (client == null) {
      LOG.warn(
          "ChatClient unavailable · activeProvider={} tenantId={} · 检查 longfeng.ai.provider 配置",
          activeProvider,
          tenantId);
      throw new BusinessException(
          ErrCode.AI_PROVIDER_UNAVAILABLE, "msgkey:ai.error.provider_unavailable");
    }
    LOG.debug("ChatClient resolved · provider={} tenantId={}", activeProvider, tenantId);
    return client;
  }

  /** 当前激活 provider 名（fallback chain 排除当前激活时使用）。 */
  public String activeProvider() {
    return activeProvider;
  }

  /**
   * 4 档 provider config 暴露的元信息（用于审计 / 监控）。
   *
   * @return 不可变 map · key=provider name · value=display
   */
  public Map<String, String> supportedProviders() {
    return Map.of(
        "openai", "OpenAI / Azure",
        "qianwen", "通义千问 VL（默认 国内）",
        "zhipu", "智谱 GLM-4V",
        "local-vllm", "本地 vLLM 备份");
  }
}
