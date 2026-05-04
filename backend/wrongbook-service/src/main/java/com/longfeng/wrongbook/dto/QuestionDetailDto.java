package com.longfeng.wrongbook.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * P04 Result page DTO · GET /api/wb/questions/{qid} · 1:1 aligned with FE
 * {@code frontend/apps/h5/src/pages/Result/index.tsx} interface QuestionDetail.
 *
 * <p>FE uses camelCase via destructuring; standard backend snake_case is enforced via
 * {@link JsonProperty} annotations. Fields with no annotation use record-name default
 * (id / subject / stem / formula / steps / difficulty / confidence) which already match
 * camelCase for single-word names.
 */
@Schema(description = "P04 Result · 题目详情聚合")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record QuestionDetailDto(
    @Schema(description = "wrong_item ID (string · Snowflake to avoid JS precision loss)")
        String id,
    @Schema(description = "学科 · math | physics | chemistry | english") String subject,
    @Schema(description = "题干文本") String stem,
    @Schema(description = "公式 / 表达式 (optional)") String formula,
    @Schema(description = "缩略图 URL (optional)") @JsonProperty("thumbnail_url")
        String thumbnailUrl,
    @Schema(description = "学生作答") @JsonProperty("my_answer") String myAnswer,
    @Schema(description = "正确答案") @JsonProperty("correct_answer") String correctAnswer,
    @Schema(description = "错因解析 (markdown · 来自 AI analysis explain 字段)")
        @JsonProperty("reason_markdown")
        String reasonMarkdown,
    @Schema(description = "解题步骤") List<SolutionStep> steps,
    @Schema(description = "知识点 / 标签") @JsonProperty("knowledge_points")
        List<KnowledgePoint> knowledgePoints,
    @Schema(description = "难度 1-5") Integer difficulty,
    @Schema(description = "AI 置信度 0-1") Double confidence,
    @Schema(description = "调用模型信息") @JsonProperty("model_info") ModelInfo modelInfo) {

  /** 解题步骤 · idx / title / detail? / formula? */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record SolutionStep(int idx, String title, String detail, String formula) {}

  /** 知识点 · id / name / weight (0-1) */
  public record KnowledgePoint(String id, String name, double weight) {}

  /** LLM 调用记录 · name / version */
  public record ModelInfo(String name, String version) {}
}
