package com.longfeng.aianalysis.llm;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Spring AI {@code ChatClient.call().entity(AnalysisResult.class)} 的目标结构（TDD §6.4）。
 *
 * <p>必须与 {@code prompt/wrong-question-analysis.st} 的 {@code output_schema} 1:1 对齐。
 * 字段：OCR 文本 / 学科 / 知识点 / 错因 / 解法步骤 / 难度 / 变式题。
 *
 * <p>由 BE-07-ai 拥有 · 与现有 {@code service/dto/AnalysisVO} 不同 ——
 * AnalysisVO 是 REST 出参（含 status / version / model_provider 等元数据），
 * AnalysisResult 是 LLM 直接输出形状。
 *
 * @param stem            题干 OCR 文本
 * @param subject         学科枚举（MATH / CHINESE / ENGLISH / PHYSICS / CHEMISTRY）
 * @param knowledgePoints 知识点标签数组
 * @param errorType       错因类型 (CONCEPT / CALCULATION / COMPREHENSION / HANDWRITING / OTHER)
 * @param errorReason     错因解释（短文 ≤ 60 字）
 * @param solutionSteps   解法步骤数组（每步一行 markdown）
 * @param difficulty      难度 1-5
 * @param variants        变式题数组（≤ 3 题）
 */
@Schema(description = "AI 多模态结构化输出 · entity(AnalysisResult.class)")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AnalysisResult(
    @JsonProperty("stem") String stem,
    @JsonProperty("subject") String subject,
    @JsonProperty("knowledgePoints") List<String> knowledgePoints,
    @JsonProperty("errorType") String errorType,
    @JsonProperty("errorReason") String errorReason,
    @JsonProperty("solutionSteps") List<String> solutionSteps,
    @JsonProperty("difficulty") Integer difficulty,
    @JsonProperty("variants") List<String> variants) {

  /** Helper: 兼容前端 stemText 字段命名（s7 frontend 已用）。 */
  public String stemText() {
    return stem;
  }
}
