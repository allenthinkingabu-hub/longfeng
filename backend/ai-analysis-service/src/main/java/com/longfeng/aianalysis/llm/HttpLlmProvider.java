package com.longfeng.aianalysis.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Transport-agnostic okhttp wrapper — concrete dashscope / openai providers configure base URL,
 * headers, and prompt-body translation via {@link Endpoint}. Intentionally small; swap to Spring
 * AI ChatClient in a follow-up once 1.0 GA is published (ADR 0014).
 */
public abstract class HttpLlmProvider implements LlmProvider {

  private static final Logger LOG = LoggerFactory.getLogger(HttpLlmProvider.class);
  private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

  protected final String providerName;
  protected final String chatModel;
  protected final String embedModel;
  protected final Endpoint chatEndpoint;
  protected final Endpoint embedEndpoint;
  protected final OkHttpClient http;
  protected final ObjectMapper om;
  protected final String apiKey;

  protected HttpLlmProvider(
      String providerName,
      String chatModel,
      String embedModel,
      Endpoint chatEndpoint,
      Endpoint embedEndpoint,
      String apiKey,
      ObjectMapper om) {
    this.providerName = providerName;
    this.chatModel = chatModel;
    this.embedModel = embedModel;
    this.chatEndpoint = chatEndpoint;
    this.embedEndpoint = embedEndpoint;
    this.apiKey = apiKey;
    this.om = om;
    this.http =
        new OkHttpClient.Builder()
            .callTimeout(Duration.ofSeconds(30))
            .connectTimeout(Duration.ofSeconds(5))
            .readTimeout(Duration.ofSeconds(25))
            .build();
  }

  @Override
  public String name() {
    return providerName;
  }

  @Override
  public ChatResponse chat(String prompt, Map<String, Object> options) {
    long start = System.currentTimeMillis();
    String body = chatEndpoint.buildBody(chatModel, prompt, options, om);
    String raw = post(chatEndpoint.url, body);
    long latency = System.currentTimeMillis() - start;
    return chatEndpoint.parseResponse(raw, chatModel, latency, om);
  }

  @Override
  public float[] embed(String text) {
    long start = System.currentTimeMillis();
    String body = embedEndpoint.buildBody(embedModel, text, Map.of(), om);
    String raw = post(embedEndpoint.url, body);
    long latency = System.currentTimeMillis() - start;
    LOG.debug("embed {} call {}ms", providerName, latency);
    return embedEndpoint.parseEmbedding(raw);
  }

  private String post(String url, String body) {
    Request req =
        new Request.Builder()
            .url(url)
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "application/json")
            .post(RequestBody.create(body, JSON))
            .build();
    try (Response resp = http.newCall(req).execute()) {
      String payload = resp.body() == null ? "" : resp.body().string();
      if (!resp.isSuccessful()) {
        throw new LlmTransportException(
            providerName + " " + resp.code() + " body=" + truncate(payload));
      }
      return payload;
    } catch (IOException e) {
      throw new LlmTransportException(providerName + " io-error: " + e.getMessage(), e);
    }
  }

  private static String truncate(String s) {
    return s == null || s.length() <= 300 ? s : s.substring(0, 300) + "…";
  }

  /** Per-provider URL + body/response mapping. Concrete classes populate this. */
  public record Endpoint(
      String url, BodyBuilder bodyBuilder, ChatParser chatParser, EmbedParser embedParser) {
    public String buildBody(
        String model, String inputOrPrompt, Map<String, Object> options, ObjectMapper om) {
      return bodyBuilder.build(model, inputOrPrompt, options, om);
    }

    public ChatResponse parseResponse(String raw, String model, long latency, ObjectMapper om) {
      return chatParser.parse(raw, model, latency, om);
    }

    public float[] parseEmbedding(String raw) {
      return embedParser.parse(raw);
    }

    public interface BodyBuilder {
      String build(
          String model, String inputOrPrompt, Map<String, Object> options, ObjectMapper om);
    }

    public interface ChatParser {
      ChatResponse parse(String raw, String model, long latencyMs, ObjectMapper om);
    }

    public interface EmbedParser {
      float[] parse(String raw);
    }
  }

  /** Network / transport failure — ProviderRouter treats this as grounds for fallback. */
  public static class LlmTransportException extends RuntimeException {
    public LlmTransportException(String msg) {
      super(msg);
    }

    public LlmTransportException(String msg, Throwable cause) {
      super(msg, cause);
    }
  }
}
