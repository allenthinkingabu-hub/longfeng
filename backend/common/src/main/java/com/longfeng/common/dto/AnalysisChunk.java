package com.longfeng.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * SSE / WebSocket chunk payload — D-AI-Stream 决策（TDD §0.9 + §12.2.1）。
 *
 * <p>同一 {@code Sinks.Many<AnalysisChunk>} 源被 SSE 端点（H5）与 WebSocket Handler（小程序）共同消费，
 * 业务实现单源（TDD §8.1）。
 *
 * <p><b>WT4 · 2026-05-04 重构</b>：FE H5 客户端 ({@code useEventSource.ts}) 通过 fetch + ReadableStream
 * 解析 SSE · 按 JSON {@code type} 字段分发 6 种 event：
 *
 * <ul>
 *   <li>{@code STEP_START} — 单步开始 · 含 {@code step:1|2|3|4}
 *   <li>{@code STEP_DONE} — 单步完成 · 含 {@code step}, {@code durationMs}
 *   <li>{@code PARTIAL_JSON} — LLM 流式 JSON 片段 · 累加到 {@code partialJson}
 *   <li>{@code DONE} — 终结 · 成功
 *   <li>{@code FAIL} — 终结 · 失败 · 含 {@code step?}, {@code errorCode?}
 *   <li>{@code CANCELLED} — 终结 · 用户取消
 * </ul>
 *
 * <p>历史 {@code Stage} enum (OCR/ANALYSIS/STEPS/DONE/FAIL) 与 4-step 概念保留以兼容
 * WS 小程序端 + 已存 IT · 通过 {@code type} 字段 + factory 映射兼容前端。
 *
 * <p>4 步流水线映射（FE STYLE-TRUTH §3 P03 spec §5）：
 *
 * <ul>
 *   <li>step 1 — 图像预处理（旧 OCR 阶段）
 *   <li>step 2 — OCR 题干（旧 ANALYSIS 阶段开始）
 *   <li>step 3 — 错因诊断（旧 STEPS 阶段）
 *   <li>step 4 — 生成解法（旧 DONE 阶段前）
 * </ul>
 *
 * <p>S3 BE-07-ai 共享 DTO · 由 ai-analysis-service 产生 · wrongbook-service / 前端 H5 / 小程序消费。
 *
 * @param type        FE-aligned event type · 客户端解析入口（不为 null）
 * @param stage       历史 stage enum · WS handler / IT 兼容用 · 可空
 * @param step        4-step 流水线步骤 1..4 · STEP_START / STEP_DONE / FAIL 时使用
 * @param durationMs  STEP_DONE 携带的耗时
 * @param partialJson PARTIAL_JSON 流式片段（FE 累加）· 历史 STEPS/DONE 也携带完整结构
 * @param chunk       历史文本片段（ANALYSIS 流式 + FAIL errorCode）· 兼容用
 * @param errorCode   FAIL 事件的错误码
 * @param progressPct 进度百分比 0-100
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AnalysisChunk(
    @JsonProperty("type") Type type,
    @JsonProperty("stage") Stage stage,
    @JsonProperty("step") Integer step,
    @JsonProperty("durationMs") Long durationMs,
    @JsonProperty("partialJson") Object partialJson,
    @JsonProperty("chunk") String chunk,
    @JsonProperty("errorCode") String errorCode,
    @JsonProperty("progressPct") int progressPct) {

  /** Compact convenience ctor: legacy 4-arg shape (stage / chunk / partialJson / progressPct). */
  public AnalysisChunk(Stage stage, String chunk, Object partialJson, int progressPct) {
    this(mapStageToType(stage), stage, null, null, partialJson, chunk, null, progressPct);
  }

  /** FE-aligned event type · {@code data: {"type":"..."}} 字段 · 客户端按此分发。 */
  public enum Type {
    STEP_START,
    STEP_DONE,
    PARTIAL_JSON,
    DONE,
    FAIL,
    CANCELLED
  }

  /** D-AI-Stream 历史 stage 枚举（TDD §12.2.1）· WS/IT 兼容用。 */
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
    FAIL,
    /** D-AI-Cancel 用户取消（WT4 新增）· progressPct=0。 */
    CANCELLED
  }

  /** 历史 stage → FE Type 映射（兼容已写代码）。 */
  private static Type mapStageToType(Stage s) {
    if (s == null) return null;
    return switch (s) {
      case OCR, ANALYSIS, STEPS -> Type.PARTIAL_JSON;
      case DONE -> Type.DONE;
      case FAIL -> Type.FAIL;
      case CANCELLED -> Type.CANCELLED;
    };
  }

  // ---------------- legacy stage factories (兼容 WS / IT) ----------------

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
    return new AnalysisChunk(Type.DONE, Stage.DONE, null, null, result, null, null, 100);
  }

  /** Convenience factory for FAIL frame carrying error code in {@code chunk} field. */
  public static AnalysisChunk fail(String errorCode) {
    return new AnalysisChunk(Type.FAIL, Stage.FAIL, null, null, null, errorCode, errorCode, 0);
  }

  // ---------------- FE-aligned factories (WT4 新增) ----------------

  /** STEP_START · step 1..4 · 推送给 FE 切换流水线动画。 */
  public static AnalysisChunk stepStart(int step) {
    return new AnalysisChunk(Type.STEP_START, null, step, null, null, null, null, step * 25);
  }

  /** STEP_DONE · step + duration · FE 显示耗时 chip。 */
  public static AnalysisChunk stepDone(int step, long durationMs) {
    return new AnalysisChunk(
        Type.STEP_DONE, null, step, durationMs, null, null, null, step * 25);
  }

  /** PARTIAL_JSON · LLM 流式输出片段 · FE 拼接。 */
  public static AnalysisChunk partialJson(String fragment) {
    return new AnalysisChunk(Type.PARTIAL_JSON, null, null, null, fragment, null, null, 75);
  }

  /** CANCELLED · 用户取消的终结事件 · POST /api/ai/cancel/{taskId} 之后由 hub 推送。 */
  public static AnalysisChunk cancelled() {
    return new AnalysisChunk(Type.CANCELLED, Stage.CANCELLED, null, null, null, null, null, 0);
  }

  /** FAIL with explicit step + error code (WT4 新增 · 前端按 step 标 fail 状态)。 */
  public static AnalysisChunk failAtStep(int step, String errorCode) {
    return new AnalysisChunk(Type.FAIL, Stage.FAIL, step, null, null, errorCode, errorCode, 0);
  }
}
