package com.longfeng.aianalysis.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.longfeng.aianalysis.stub.ChatClient;
import com.longfeng.common.exception.BusinessException;
import com.longfeng.common.exception.ErrCode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 阿里云 DashScope（OpenAI 兼容模式）真实 {@link ChatClient} 实现 · BUG-LF-19 fix。
 *
 * <p>替代 {@link com.longfeng.aianalysis.stub.StubChatClient}（length-based 估算 token） · 让
 * ai_usage_log 写真实 token 计费。
 *
 * <p>端点：{@code https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions}<br>
 * 模型：{@code qwen-vl-max}（多模态视觉语言）
 *
 * <p>请求格式（OpenAI Chat Completions 兼容）：
 * <pre>{@code
 * {
 *   "model": "qwen-vl-max",
 *   "messages": [
 *     {"role": "system", "content": "<prompt 模板>"},
 *     {"role": "user", "content": [
 *       {"type": "image_url", "image_url": {"url": "data:image/jpeg;base64,..."}},
 *       {"type": "text", "text": "请按 schema 返回 JSON"}
 *     ]}
 *   ],
 *   "temperature": 0.2,
 *   "response_format": {"type": "json_object"}
 * }
 * }</pre>
 *
 * <p>响应解析：
 * <ul>
 *   <li>{@code choices[0].message.content} → JSON 字符串 → {@link AnalysisResult}
 *   <li>{@code usage.prompt_tokens} / {@code usage.completion_tokens} → {@link Usage}
 * </ul>
 *
 * <p>异常处理：HTTP non-2xx 抛 {@link BusinessException}({@link ErrCode#AI_PROVIDER_UNAVAILABLE})
 * 让 {@link com.longfeng.aianalysis.support.FallbackOrchestrator} 接管降级。
 */
public class DashscopeChatClient implements ChatClient {

  private static final Logger LOG = LoggerFactory.getLogger(DashscopeChatClient.class);

  private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
  private static final String CHAT_COMPLETIONS_PATH = "/chat/completions";
  private static final String DEFAULT_PROVIDER_NAME = "qianwen";

  private final String apiKey;
  private final String baseUrl;
  private final String model;
  private final OkHttpClient httpClient;
  private final ObjectMapper objectMapper;
  /** Provider name 暴露给 ChatClientFactory · 默认 "qianwen"（与 longfeng.ai.provider 对齐）。 */
  private final String providerName;

  public DashscopeChatClient(String apiKey, String baseUrl, String model, ObjectMapper om) {
    this(apiKey, baseUrl, model, om, DEFAULT_PROVIDER_NAME, defaultHttpClient());
  }

  /** Test 友好构造器 · 注入 OkHttpClient 方便 wiremock 拦截。 */
  DashscopeChatClient(
      String apiKey,
      String baseUrl,
      String model,
      ObjectMapper om,
      String providerName,
      OkHttpClient httpClient) {
    if (apiKey == null || apiKey.isBlank()) {
      throw new IllegalArgumentException("DashScope apiKey 不能为空");
    }
    if (baseUrl == null || baseUrl.isBlank()) {
      throw new IllegalArgumentException("DashScope baseUrl 不能为空");
    }
    this.apiKey = apiKey;
    this.baseUrl = stripTrailingSlash(baseUrl);
    this.model = (model == null || model.isBlank()) ? "qwen-vl-max" : model;
    this.objectMapper = om;
    this.providerName = providerName;
    this.httpClient = httpClient;
  }

  private static OkHttpClient defaultHttpClient() {
    return new OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build();
  }

  private static String stripTrailingSlash(String url) {
    return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
  }

  @Override
  public ChatResponse analyze(String prompt, String imageBase64OrPath, String subject) {
    long startedAt = System.currentTimeMillis();
    try {
      String imageUrl = resolveImageUrl(imageBase64OrPath);
      String body = buildRequestBody(prompt, imageUrl);
      Request request =
          new Request.Builder()
              .url(baseUrl + CHAT_COMPLETIONS_PATH)
              .header("Authorization", "Bearer " + apiKey)
              .header("Content-Type", "application/json")
              .post(RequestBody.create(body, JSON))
              .build();

      try (Response response = httpClient.newCall(request).execute()) {
        long latencyMs = System.currentTimeMillis() - startedAt;
        if (!response.isSuccessful()) {
          String errorBody = bodyAsString(response.body());
          LOG.warn(
              "DashScope HTTP {} · provider={} model={} latencyMs={} body={}",
              response.code(),
              providerName,
              model,
              latencyMs,
              truncate(errorBody, 500));
          throw new BusinessException(
              ErrCode.AI_PROVIDER_UNAVAILABLE, "msgkey:ai.error.provider_unavailable");
        }

        String responseBody = bodyAsString(response.body());
        JsonNode root = objectMapper.readTree(responseBody);
        AnalysisResult result = parseResult(root, subject);
        Usage usage = parseUsage(root);

        LOG.info(
            "DashScope analyze · provider={} model={} latencyMs={} promptTokens={} completionTokens={}",
            providerName,
            model,
            latencyMs,
            usage.promptTokens(),
            usage.completionTokens());

        return new ChatResponse(result, usage);
      }
    } catch (BusinessException be) {
      throw be;
    } catch (IOException ioe) {
      LOG.warn(
          "DashScope IO error · provider={} model={} cause={}",
          providerName,
          model,
          ioe.getMessage());
      throw new BusinessException(
          ErrCode.AI_PROVIDER_UNAVAILABLE, "msgkey:ai.error.provider_unavailable", ioe);
    } catch (RuntimeException re) {
      LOG.warn(
          "DashScope unexpected error · provider={} model={} cause={}",
          providerName,
          model,
          re.getMessage());
      throw new BusinessException(
          ErrCode.AI_PROVIDER_UNAVAILABLE, "msgkey:ai.error.provider_unavailable", re);
    }
  }

  @Override
  public String providerName() {
    return providerName;
  }

  /**
   * 把 spool 文件路径转 {@code data:image/jpeg;base64,...} URI · 适配 OpenAI 兼容 image_url 格式。
   *
   * <p>支持三种入参：
   * <ul>
   *   <li>{@code data:image/...;base64,XXX} — 直接透传
   *   <li>{@code http(s)://...} — 直接透传（DashScope 也支持远程 URL）
   *   <li>本地文件路径 — 读字节 → base64 → data URI
   * </ul>
   */
  String resolveImageUrl(String imageBase64OrPath) {
    if (imageBase64OrPath == null || imageBase64OrPath.isBlank()) {
      // 无图片时仍发请求 · 让 LLM 据 prompt 文本响应（QA 测试场景）
      return null;
    }
    String trimmed = imageBase64OrPath.trim();
    if (trimmed.startsWith("data:") || trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
      return trimmed;
    }
    // 当作本地文件路径 · 读字节转 base64
    try {
      byte[] bytes = Files.readAllBytes(Path.of(trimmed));
      String base64 = Base64.getEncoder().encodeToString(bytes);
      String mime = sniffMime(trimmed);
      return "data:" + mime + ";base64," + base64;
    } catch (IOException ex) {
      LOG.warn("DashScope failed to read image file · path={} cause={}", trimmed, ex.getMessage());
      throw new BusinessException(
          ErrCode.AI_PROVIDER_UNAVAILABLE, "msgkey:ai.error.provider_unavailable", ex);
    }
  }

  private static String sniffMime(String path) {
    String lower = path.toLowerCase(java.util.Locale.ROOT);
    if (lower.endsWith(".png")) return "image/png";
    if (lower.endsWith(".webp")) return "image/webp";
    if (lower.endsWith(".heic")) return "image/heic";
    return "image/jpeg";
  }

  /** 拼 OpenAI Chat Completions 兼容 body · system + user(image+text) · response_format=json_object。 */
  String buildRequestBody(String prompt, String imageUrl) {
    ObjectNode root = objectMapper.createObjectNode();
    root.put("model", model);
    root.put("temperature", 0.2);

    ArrayNode messages = root.putArray("messages");

    // System message · 直接用模板（已在上层 PromptInjectionGuard 通过）
    ObjectNode sys = messages.addObject();
    sys.put("role", "system");
    sys.put("content", prompt == null ? "" : prompt);

    // User message · multimodal content array
    ObjectNode user = messages.addObject();
    user.put("role", "user");
    ArrayNode userContent = user.putArray("content");
    if (imageUrl != null) {
      ObjectNode imgPart = userContent.addObject();
      imgPart.put("type", "image_url");
      ObjectNode imgUrl = imgPart.putObject("image_url");
      imgUrl.put("url", imageUrl);
    }
    ObjectNode textPart = userContent.addObject();
    textPart.put("type", "text");
    textPart.put("text", "请严格按 system 中 output_schema 描述返回 JSON · 仅返回 JSON 不要 markdown 围栏");

    // 强制 JSON 输出 · OpenAI 兼容
    ObjectNode rf = root.putObject("response_format");
    rf.put("type", "json_object");

    try {
      return objectMapper.writeValueAsString(root);
    } catch (IOException ex) {
      throw new BusinessException(
          ErrCode.AI_PROVIDER_UNAVAILABLE, "msgkey:ai.error.provider_unavailable", ex);
    }
  }

  /**
   * 从 DashScope 响应中提取 {@code choices[0].message.content} → 解析为 {@link AnalysisResult}。
   *
   * <p>容忍 LLM 偶发包 markdown 围栏（{@code ```json ... ```}） · 截断后 parse。
   */
  AnalysisResult parseResult(JsonNode root, String subjectHint) throws IOException {
    JsonNode choices = root.path("choices");
    if (!choices.isArray() || choices.isEmpty()) {
      throw new BusinessException(
          ErrCode.AI_PROVIDER_UNAVAILABLE, "msgkey:ai.error.provider_unavailable");
    }
    String content = choices.get(0).path("message").path("content").asText("");
    if (content.isBlank()) {
      throw new BusinessException(
          ErrCode.AI_PROVIDER_UNAVAILABLE, "msgkey:ai.error.provider_unavailable");
    }
    String json = stripMarkdownFence(content);
    AnalysisResult parsed = objectMapper.readValue(json, AnalysisResult.class);
    // subject 兜底（LLM 偶发漏填） · 用调用方 hint
    if ((parsed.subject() == null || parsed.subject().isBlank()) && subjectHint != null) {
      return new AnalysisResult(
          parsed.stem(),
          subjectHint,
          parsed.knowledgePoints(),
          parsed.errorType(),
          parsed.errorReason(),
          parsed.solutionSteps(),
          parsed.difficulty(),
          parsed.variants());
    }
    return parsed;
  }

  /** 抽 {@code usage.prompt_tokens} + {@code usage.completion_tokens} · 缺字段返 zero。 */
  Usage parseUsage(JsonNode root) {
    JsonNode usage = root.path("usage");
    if (usage.isMissingNode() || usage.isNull()) {
      return Usage.zero();
    }
    int promptTokens = usage.path("prompt_tokens").asInt(0);
    int completionTokens = usage.path("completion_tokens").asInt(0);
    return new Usage(promptTokens, completionTokens);
  }

  private static String stripMarkdownFence(String raw) {
    String s = raw.trim();
    if (s.startsWith("```")) {
      int firstNl = s.indexOf('\n');
      if (firstNl > 0) {
        s = s.substring(firstNl + 1);
      }
      if (s.endsWith("```")) {
        s = s.substring(0, s.length() - 3);
      }
    }
    return s.trim();
  }

  private static String bodyAsString(ResponseBody body) throws IOException {
    return body == null ? "" : body.string();
  }

  private static String truncate(String s, int max) {
    if (s == null) return "";
    return s.length() <= max ? s : s.substring(0, max) + "...(truncated)";
  }
}
