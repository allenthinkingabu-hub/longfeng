package com.longfeng.aianalysis.stub;

import com.longfeng.aianalysis.llm.AnalysisResult;

/**
 * 自定义 ChatClient 接口 · 替代 Spring AI 1.0.0-M1 {@code ChatClient}（C-14 修复）。
 *
 * <p>设计原则：
 * <ul>
 *   <li>不依赖任何 Spring AI artifact（C-14 根因：M1 API 已被 1.0.0 GA 重命名）
 *   <li>仅暴露 AI 分析服务真正需要的能力 · 不过度抽象
 *   <li>stub 实现返回 placeholder {@link AnalysisResult} · 不调真 LLM
 *   <li>S9 QA Agent b 轨 e2e 可跑 · A 轨真 LLM 留 user 后续接真 API key
 * </ul>
 *
 * <p>真实 LLM 路径（未来 A 轨）：用 okhttp / Spring WebClient 封装同一接口 · 切换 impl bean 即可，
 * 业务代码（QuestionAnalyzerImpl）零修改。
 */
public interface ChatClient {

  /**
   * 发送多模态提示词 · 返回结构化分析结果。
   *
   * @param prompt 拼装好的提示词文本（含 subject / grade / ocrHint 占位变量已替换）
   * @param imageBase64OrPath 图片路径或 base64（stub 实现忽略此参数 · 真实实现按 provider 协议组包）
   * @param subject 学科枚举 hint（MATH / CHINESE / PHYSICS / ...）
   * @return 结构化分析结果 · stub 固定返回 placeholder
   */
  AnalysisResult analyze(String prompt, String imageBase64OrPath, String subject);

  /**
   * 健康探针 · 返回此 client 对应的 provider 名称。
   *
   * @return provider name (qianwen / openai / zhipu / local-vllm / stub)
   */
  String providerName();
}
