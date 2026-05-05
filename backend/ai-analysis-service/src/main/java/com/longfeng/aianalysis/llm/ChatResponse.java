package com.longfeng.aianalysis.llm;

/**
 * {@link com.longfeng.aianalysis.stub.ChatClient#analyze} 返回值 · 包结构化结果 + 真实 token usage
 * （BUG-LF-19 fix）。
 *
 * <p>设计目的：把 ai_usage_log 写库的 token 数据从 length-based 估算升级到 LLM 厂商
 * response.usage 字段的真值 · 让监控 / 计费 / 配额都看到真实消费。
 *
 * <p>Stub 实现填 {@link Usage#zero()} · 调用方据此走 length 估算回退（保持向后兼容）。
 */
public record ChatResponse(AnalysisResult result, Usage usage) {}
