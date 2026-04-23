package com.longfeng.aianalysis.llm;

import java.util.List;
import java.util.Map;

/**
 * Provider abstraction — every implementation MUST accept already-sanitized input (PII redacted
 * upstream by {@code PIIRedactor}). Two real impls (dashscope / openai) + one test stub.
 * Spring AI 1.0.0-M1 milestone fall-back: this interface stays provider-neutral so the okhttp-
 * based transport today can swap for Spring AI ChatClient when 1.0 GA ships (ADR 0014).
 */
public interface LlmProvider {

  /** Provider name used for Feature Flag match + ai_usage_log.provider column. */
  String name();

  /** Chat completion — returns structured response matching the prompt output_schema. */
  ChatResponse chat(String prompt, Map<String, Object> options);

  /** Embedding vector — 1024 dimensions (INV-05). */
  float[] embed(String text);

  /** Output from a chat call · tokens used + raw JSON payload for traceability. */
  record ChatResponse(
      String rawJson, Integer tokensIn, Integer tokensOut, long latencyMs, String model) {}

  /** Output of an embedding call · dims + tokens + model. */
  record EmbedResponse(float[] embedding, Integer tokensIn, long latencyMs, String model) {
    public List<Float> asList() {
      List<Float> list = new java.util.ArrayList<>(embedding.length);
      for (float v : embedding) list.add(v);
      return list;
    }
  }
}
