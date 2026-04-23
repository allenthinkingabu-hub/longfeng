package com.longfeng.aianalysis.llm;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Feature-Flag-driven provider selection. Default dashscope (ADR 0011) · fallback openai when the
 * primary call throws transport / rate-limit errors. Both-failed → caller writes status=9
 * (pending_analysis).
 */
@Component
public class ProviderRouter {

  private static final Logger LOG = LoggerFactory.getLogger(ProviderRouter.class);

  private final Map<String, LlmProvider> providers;

  @Value("${feature.llm-provider:dashscope}")
  private String preferredName;

  public ProviderRouter(Map<String, LlmProvider> providers) {
    this.providers = providers;
  }

  public String currentProviderName() {
    return preferredName;
  }

  public void setPreferred(String name) {
    this.preferredName = name;
  }

  public LlmProvider primary() {
    LlmProvider p = lookup(preferredName);
    if (p == null) {
      p = providers.values().stream().findFirst().orElseThrow(() -> new NoProviderException("no LlmProvider bean"));
      LOG.warn("preferred provider {} absent · using {}", preferredName, p.name());
    }
    return p;
  }

  public LlmProvider fallback() {
    return providers.values().stream()
        .filter(p -> !p.name().equalsIgnoreCase(preferredName))
        .findFirst()
        .orElse(null);
  }

  /** Execute chat with fallback — returns first successful response; throws if both fail. */
  public LlmProvider.ChatResponse chatWithFallback(String prompt, Map<String, Object> options) {
    LlmProvider first = primary();
    try {
      return first.chat(prompt, options);
    } catch (RuntimeException primaryEx) {
      LOG.warn("primary provider {} failed: {}", first.name(), primaryEx.getMessage());
      LlmProvider alt = fallback();
      if (alt == null) {
        throw primaryEx;
      }
      try {
        return alt.chat(prompt, options);
      } catch (RuntimeException altEx) {
        LOG.warn("fallback provider {} failed: {}", alt.name(), altEx.getMessage());
        throw new AllProvidersFailedException(first.name(), alt.name(), altEx);
      }
    }
  }

  /** Embed with fallback — same semantics as {@link #chatWithFallback(String, Map)}. */
  public float[] embedWithFallback(String text) {
    LlmProvider first = primary();
    try {
      return first.embed(text);
    } catch (RuntimeException primaryEx) {
      LOG.warn("primary embed {} failed: {}", first.name(), primaryEx.getMessage());
      LlmProvider alt = fallback();
      if (alt == null) {
        throw primaryEx;
      }
      try {
        return alt.embed(text);
      } catch (RuntimeException altEx) {
        LOG.warn("fallback embed {} failed: {}", alt.name(), altEx.getMessage());
        throw new AllProvidersFailedException(first.name(), alt.name(), altEx);
      }
    }
  }

  private LlmProvider lookup(String name) {
    for (LlmProvider p : providers.values()) {
      if (p.name().equalsIgnoreCase(name)) {
        return p;
      }
    }
    return null;
  }

  public static class NoProviderException extends RuntimeException {
    public NoProviderException(String msg) {
      super(msg);
    }
  }

  /** Terminal — AnalysisService catches this to write status=9 (pending_analysis). */
  public static class AllProvidersFailedException extends RuntimeException {
    public AllProvidersFailedException(String primary, String fallback, Throwable cause) {
      super("LLM providers failed · primary=" + primary + " fallback=" + fallback, cause);
    }
  }
}
