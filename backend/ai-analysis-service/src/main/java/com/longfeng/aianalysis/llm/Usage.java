package com.longfeng.aianalysis.llm;

/**
 * LLM API 真实 token 消费 · 由 provider 从 response.usage 字段抽取（BUG-LF-19 fix）。
 *
 * <p>{@link #zero()} 用于 stub 模式（无真实计费） · 调用方据此回退到 length-based 估算
 * 防止 ai_usage_log 写入 0 误导监控。
 */
public record Usage(int promptTokens, int completionTokens) {

  /** Stub 路径或异常分支用 · 0 提示调用方走 length-based 估算降级。 */
  public static Usage zero() {
    return new Usage(0, 0);
  }

  /** 是否为 zero usage（调用方判断是否回退到 length 估算）。 */
  public boolean isZero() {
    return promptTokens == 0 && completionTokens == 0;
  }
}
