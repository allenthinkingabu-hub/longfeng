package com.longfeng.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * SSE / WebSocket chunk payload — D-AI-Stream 决策（TDD §0.9 + §12.2.1）。
 *
 * <p>同一 {@code Sinks.Many<AnalysisChunk>} 源被 SSE 端点（H5）与 WebSocket Handler（小程序）共同消费，
 * 业务实现单源（TDD §8.1）。
 *
 * <p>4 个 stage 阶段（不可变枚举）：
 *
 * <ul>
 *   <li>{@code OCR} — 多模态 OCR 完成
 *   <li>{@code ANALYSIS} — LLM 推理中（chunk 为流式片段）
 *   <li>{@code STEPS} — 解题步骤生成中（partialJson 为半结构）
 *   <li>{@code DONE} — 终态（partialJson = 完整 AnalysisResult）
 * </ul>
 *
 * <p>S3 BE-07-ai 共享 DTO · 由 ai-analysis-service 产生 · wrongbook-service / 前端 H5 / 小程序消费。
 * 放 backend/common.dto 而不是各 service 内部，是因为 D-FE-Contract 要求"零手写 DTO"——
 * 该结构会被 OpenAPI generator 拣到 frontend/packages/api-contracts/src/gen/。
 *
 * @param stage       4 stage 之一（不为 null）
 * @param chunk       文本片段（流式 ANALYSIS 阶段使用 · 其他阶段可为 null）
 * @param partialJson 半结构 JSON（STEPS / DONE 阶段使用 · 其他阶段为 null）
 * @param progressPct 进度百分比 0-100（OCR=20 / ANALYSIS=50 / STEPS=80 / DONE=100）
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AnalysisChunk(
    @JsonProperty("stage") Stage stage,
    @JsonProperty("chunk") String chunk,
    @JsonProperty("partialJson") Object partialJson,
    @JsonProperty("progressPct") int progressPct) {

  /** D-AI-Stream 4 stage 枚举（TDD §12.2.1）。 */
  public enum Stage {
    /** OCR 完成 · progressPct=20。 */
    OCR,
    /** LLM 推理中 · progressPct=50 · 流式 chunk。 */
    ANALYSIS,
    /** 解题步骤生成中 · progressPct=80。 */
    STEPS,
    /** 终态完成 · progressPct=100 · partialJson = 完整 AnalysisResult。 */
    DONE,
    /** 失败终态（D-AI-Cancel 取消 / Provider 失败 / 注入拦截）· progressPct=0 · chunk=errCode。 */
    FAIL
  }

  /** Convenience factory for OCR completion frame. */
  public static AnalysisChunk ocr() {
    return new AnalysisChunk(Stage.OCR, null, null, 20);
  }

  /** Convenience factory for an ANALYSIS streaming chunk. */
  public static AnalysisChunk analysis(String chunk) {
    return new AnalysisChunk(Stage.ANALYSIS, chunk, null, 50);
  }

  /** Convenience factory for STEPS partial JSON frame. */
  public static AnalysisChunk steps(Object partialJson) {
    return new AnalysisChunk(Stage.STEPS, null, partialJson, 80);
  }

  /** Convenience factory for DONE terminal frame with full result payload. */
  public static AnalysisChunk done(Object result) {
    return new AnalysisChunk(Stage.DONE, null, result, 100);
  }

  /** Convenience factory for FAIL frame carrying error code in {@code chunk} field. */
  public static AnalysisChunk fail(String errorCode) {
    return new AnalysisChunk(Stage.FAIL, errorCode, null, 0);
  }
}
