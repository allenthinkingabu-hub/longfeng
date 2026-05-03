package com.longfeng.aianalysis.controller;

import com.longfeng.aianalysis.service.dto.AiModelInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * D-AI-Model-Catalog API · SC-16 (plan §5.S3 + §0.9)。
 *
 * <p>端点：{@code GET /api/ai/models}
 * <ul>
 *   <li>返回 4 模型 stub catalog（qwen-vl-max / gpt-4o-mini / glm-4v / claude-opus）
 *   <li>按 {@code X-User-Tier} header 过滤：NORMAL 只能看非 vipOnly 模型 · VIP/VIP_PLUS 可见全部
 *   <li>C-25 准备：此 endpoint 也作为 Orval 代码生成的来源
 * </ul>
 *
 * <p>Tier 规则（D-AI-Tier-Policy · plan §0.9）：
 * <ul>
 *   <li>{@code NORMAL} — 免费档 · 只可用 qwen-vl-max
 *   <li>{@code VIP} — 付费档 · 可用 qwen-vl-max + gpt-4o-mini + glm-4v
 *   <li>{@code VIP_PLUS} — 高级档 · 可用全部 4 模型（含 claude-opus）
 * </ul>
 */
@Tag(name = "ai-models", description = "D-AI-Model-Catalog · AI 模型目录 SC-16")
@RestController
@RequestMapping("/api/ai")
public class AiModelsController {

  private static final Logger LOG = LoggerFactory.getLogger(AiModelsController.class);

  /** D-AI-Model-Catalog stub 数据（4 模型 · 按 plan §5.S3 SC-16 规格）。 */
  private static final List<AiModelInfo> ALL_MODELS = List.of(
      new AiModelInfo(
          /* id             */ "qianwen:qwen-vl-max",
          /* provider       */ "qianwen",
          /* displayName    */ "通义千问 VL Max",
          /* vipOnly        */ false,
          /* subjects       */ List.of("MATH", "CHINESE", "ENGLISH", "PHYSICS", "CHEMISTRY"),
          /* costTier       */ "M",
          /* avgLatencyMs   */ 1200,
          /* notes          */ "国内默认 · DashScope · 多模态 OCR 能力强 · 国内合规首选"),

      new AiModelInfo(
          /* id             */ "openai:gpt-4o-mini",
          /* provider       */ "openai",
          /* displayName    */ "GPT-4o Mini",
          /* vipOnly        */ true,
          /* subjects       */ List.of("MATH", "CHINESE", "ENGLISH", "PHYSICS", "CHEMISTRY"),
          /* costTier       */ "L",
          /* avgLatencyMs   */ 900,
          /* notes          */ "VIP 档 · 海外备用 · 高速低成本 · 需海外 API Key"),

      new AiModelInfo(
          /* id             */ "zhipu:glm-4v",
          /* provider       */ "zhipu",
          /* displayName    */ "智谱 GLM-4V Plus",
          /* vipOnly        */ true,
          /* subjects       */ List.of("MATH", "CHINESE", "PHYSICS"),
          /* costTier       */ "M",
          /* avgLatencyMs   */ 1500,
          /* notes          */ "VIP 档 · 国内备用 · BigModel 平台 · 中文理解强"),

      new AiModelInfo(
          /* id             */ "openai:claude-opus",
          /* provider       */ "openai",
          /* displayName    */ "Claude Opus（代理）",
          /* vipOnly        */ true,
          /* subjects       */ List.of("MATH", "CHINESE", "ENGLISH", "PHYSICS", "CHEMISTRY"),
          /* costTier       */ "H",
          /* avgLatencyMs   */ 2500,
          /* notes          */ "VIP_PLUS 专属 · 最强推理 · 高成本 · 走 OpenAI 兼容代理端点"));

  @Operation(
      summary = "获取可用 AI 模型目录",
      description = "D-AI-Model-Catalog SC-16 · 按 X-User-Tier 过滤可用模型列表")
  @ApiResponse(
      responseCode = "200",
      description = "模型列表（按 tier 过滤）",
      content = @Content(
          mediaType = MediaType.APPLICATION_JSON_VALUE,
          array = @ArraySchema(schema = @Schema(implementation = AiModelInfo.class))))
  @GetMapping(value = "/models", produces = MediaType.APPLICATION_JSON_VALUE)
  public List<AiModelInfo> listModels(
      @Parameter(description = "用户订阅档位 · NORMAL / VIP / VIP_PLUS · 默认 NORMAL")
      @RequestHeader(value = "X-User-Tier", defaultValue = "NORMAL") String tier) {

    LOG.debug("GET /api/ai/models · tier={}", tier);

    // 按 tier 过滤：NORMAL 只能看 vipOnly=false 的模型
    boolean isVip = "VIP".equalsIgnoreCase(tier) || "VIP_PLUS".equalsIgnoreCase(tier);
    boolean isVipPlus = "VIP_PLUS".equalsIgnoreCase(tier);

    return ALL_MODELS.stream()
        .filter(m -> {
          if (!m.vipOnly()) {
            return true; // NORMAL 可见
          }
          if (isVipPlus) {
            return true; // VIP_PLUS 全部可见
          }
          // VIP 可见 vipOnly 但排除 claude-opus（costTier=H 仅 VIP_PLUS）
          if (isVip) {
            return !"H".equals(m.costTier());
          }
          return false;
        })
        .collect(Collectors.toList());
  }
}
