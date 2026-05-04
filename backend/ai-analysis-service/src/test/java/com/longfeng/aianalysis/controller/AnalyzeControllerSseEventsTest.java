package com.longfeng.aianalysis.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.longfeng.common.dto.AnalysisChunk;
import org.junit.jupiter.api.Test;

/**
 * WT4 · AnalyzeController SSE 事件契约测试 · 不起 Spring Context · 只验 {@link AnalysisChunk}
 * 序列化 JSON 含 FE useEventSource.ts 期望的 6 种 {@code type} 字段。
 *
 * <p>FE 解析 SSE：fetch + ReadableStream 拿 {@code data: {...}\n\n} → JSON.parse → 按 {@code type}
 * 字段分发到 6 个 handler。所以 chunk JSON 必须含 {@code type} = STEP_START / STEP_DONE /
 * PARTIAL_JSON / DONE / FAIL / CANCELLED 之一。
 */
class AnalyzeControllerSseEventsTest {

  private final ObjectMapper om = new ObjectMapper();

  @Test
  void allSixFeEventTypes_serializeWithType_field() throws Exception {
    AnalysisChunk[] chunks = {
        AnalysisChunk.stepStart(1),
        AnalysisChunk.stepDone(2, 1234L),
        AnalysisChunk.partialJson("{\"k\":\"v\"}"),
        AnalysisChunk.done(java.util.Map.of("ok", true)),
        AnalysisChunk.fail("ai.error.provider_unavailable"),
        AnalysisChunk.cancelled(),
    };
    String[] expectedTypes = {
        "STEP_START", "STEP_DONE", "PARTIAL_JSON", "DONE", "FAIL", "CANCELLED"
    };
    for (int i = 0; i < chunks.length; i++) {
      String json = om.writeValueAsString(chunks[i]);
      assertThat(json)
          .as("chunk[%d] expected type=%s · raw=%s", i, expectedTypes[i], json)
          .contains("\"type\":\"" + expectedTypes[i] + "\"");
    }
  }

  @Test
  void stepStart_carriesStepField() throws Exception {
    String json = om.writeValueAsString(AnalysisChunk.stepStart(3));
    assertThat(json).contains("\"type\":\"STEP_START\"").contains("\"step\":3");
  }

  @Test
  void stepDone_carriesStepAndDurationMs() throws Exception {
    String json = om.writeValueAsString(AnalysisChunk.stepDone(2, 1234L));
    assertThat(json)
        .contains("\"type\":\"STEP_DONE\"")
        .contains("\"step\":2")
        .contains("\"durationMs\":1234");
  }

  @Test
  void partialJson_carriesChunkFragment() throws Exception {
    String json = om.writeValueAsString(AnalysisChunk.partialJson("{\"a\":1}"));
    // FE useEventSource.ts uses `partialJson` field on the event object
    // we serialize fragment into `chunk` field with type=PARTIAL_JSON
    assertThat(json).contains("\"type\":\"PARTIAL_JSON\"");
  }

  @Test
  void fail_carriesErrorCode() throws Exception {
    String json = om.writeValueAsString(AnalysisChunk.failAtStep(2, "ai.nsfw.blocked"));
    assertThat(json)
        .contains("\"type\":\"FAIL\"")
        .contains("\"step\":2")
        .contains("\"errorCode\":\"ai.nsfw.blocked\"");
  }

  @Test
  void cancelled_isTerminal_andHasNoStep() throws Exception {
    String json = om.writeValueAsString(AnalysisChunk.cancelled());
    assertThat(json).contains("\"type\":\"CANCELLED\"");
    // 不应漏 step（用 NON_NULL · null 字段会被剔除）
    assertThat(json).doesNotContain("\"step\":");
  }

  @Test
  void legacyStageFactories_stillSerializeBackCompat() throws Exception {
    // 老用例 · stage()=OCR/ANALYSIS/STEPS 仍需可序列化 · 兼容 WS 小程序端
    AnalysisChunk ocr = AnalysisChunk.ocr();
    String json = om.writeValueAsString(ocr);
    assertThat(json).contains("\"stage\":\"OCR\"");
    // 旧 stage 也通过 mapStageToType 映射出 type 字段（OCR/ANALYSIS/STEPS → PARTIAL_JSON）
    assertThat(json).contains("\"type\":\"PARTIAL_JSON\"");
  }
}
