package com.longfeng.aianalysis.llm;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Captures every call so the WireMock IT (AnalysisE2EIT) can assert PII-free payloads without
 * spinning a real broker. The content is deterministic enough for the contract assertions in
 * §8.8 V-S4-02/-04.
 */
public class StubProvider implements LlmProvider {

  private final String nameLabel;
  private final String modelLabel;
  private volatile boolean chatFails;
  private volatile boolean embedFails;
  private final AtomicInteger chatCalls = new AtomicInteger();
  private final AtomicInteger embedCalls = new AtomicInteger();
  private String lastPrompt;
  private String lastEmbedInput;

  public StubProvider(String nameLabel, String modelLabel) {
    this(nameLabel, modelLabel, false, false);
  }

  public StubProvider(String nameLabel, String modelLabel, boolean chatFails, boolean embedFails) {
    this.nameLabel = nameLabel;
    this.modelLabel = modelLabel;
    this.chatFails = chatFails;
    this.embedFails = embedFails;
  }

  public void setChatFails(boolean fail) {
    this.chatFails = fail;
  }

  public void setEmbedFails(boolean fail) {
    this.embedFails = fail;
  }

  @Override
  public String name() {
    return nameLabel;
  }

  @Override
  public ChatResponse chat(String prompt, Map<String, Object> options) {
    chatCalls.incrementAndGet();
    lastPrompt = prompt;
    if (chatFails) {
      throw new LlmCallException("stub-chat-fail: " + nameLabel);
    }
    String json =
        "{\"explain\":\"示例讲解（stub）\",\"causeTag\":\"CONCEPT\",\"autoTags\":[\"algebra\",\"linear-equation\"]}";
    return new ChatResponse(json, 42, 77, 12L, modelLabel);
  }

  @Override
  public float[] embed(String text) {
    embedCalls.incrementAndGet();
    lastEmbedInput = text;
    if (embedFails) {
      throw new LlmCallException("stub-embed-fail: " + nameLabel);
    }
    float[] v = new float[1024];
    // Deterministic hash-backed pseudo-vector; every provider produces a unit-ish vector so
    // cosine distance comparisons in the similar-item IT still make sense.
    int h = text.hashCode();
    for (int i = 0; i < v.length; i++) {
      v[i] = ((h + i) % 101) / 100f;
    }
    return v;
  }

  public int chatCallCount() {
    return chatCalls.get();
  }

  public int embedCallCount() {
    return embedCalls.get();
  }

  public String lastPrompt() {
    return lastPrompt;
  }

  public String lastEmbedInput() {
    return lastEmbedInput;
  }

  public void reset() {
    chatCalls.set(0);
    embedCalls.set(0);
    lastPrompt = null;
    lastEmbedInput = null;
  }

  /** Signals a provider-side failure that triggers ProviderRouter to fall back / circuit-break. */
  public static class LlmCallException extends RuntimeException {
    public LlmCallException(String msg) {
      super(msg);
    }
  }
}
