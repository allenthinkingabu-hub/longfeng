package com.longfeng.aianalysis.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.longfeng.aianalysis.llm.AnalysisResult;
import com.longfeng.aianalysis.service.AnalysisStreamHub;
import com.longfeng.aianalysis.service.QuestionAnalyzer;
import com.longfeng.common.dto.AnalysisChunk;
import com.longfeng.common.dto.ApiResult;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Plan §5.S3 出口门禁：POST /api/ai/analyze 同步 + GET /api/ai/stream/{taskId} SSE 端点。
 *
 * <p>F-02 教训：避免 Mockito mock 非接口类。{@link QuestionAnalyzer} 是接口 · 可以 Mockito。
 * {@link AnalysisStreamHub} 用 inline subclass。
 */
class AnalyzeControllerTest {

  @Test
  void analyze_returnsApiResultOk_withResultPayload() throws IOException {
    AnalysisResult dummy =
        new AnalysisResult(
            "stem", "MATH", List.of("kp1"), "OTHER", "explain", List.of("step1"), 2, List.of());
    QuestionAnalyzer analyzer = Mockito.mock(QuestionAnalyzer.class);
    Mockito.when(analyzer.analyze(Mockito.anyString(), Mockito.any(Resource.class), Mockito.anyString()))
        .thenReturn(Mono.just(dummy));
    AnalysisStreamHub hub = new AnalysisStreamHub();

    AnalyzeController ctrl = new AnalyzeController(analyzer, hub);
    MockMultipartFile file = new MockMultipartFile("image", "x.jpg", "image/jpeg", "imgbytes".getBytes());

    ApiResult<AnalysisResult> resp = ctrl.analyze("task-1", "MATH", file);

    assertThat(resp.code()).isZero();
    assertThat(resp.data()).isEqualTo(dummy);
  }

  @Test
  void stream_setsRequiredHeaders_andReturnsSseEmitter() {
    QuestionAnalyzer analyzer = Mockito.mock(QuestionAnalyzer.class);
    AnalysisStreamHub hub = new AnalysisStreamHub();
    hub.getOrCreate("task-stream"); // pre-create sink so SSE can subscribe

    AnalyzeController ctrl = new AnalyzeController(analyzer, hub);
    MockHttpServletResponse resp = new MockHttpServletResponse();

    SseEmitter emitter = ctrl.stream("task-stream", resp);

    assertThat(emitter).isNotNull();
    // TDD §8.6 必备 HTTP 头
    assertThat(resp.getHeader("X-Accel-Buffering")).isEqualTo("no");
    assertThat(resp.getHeader("Cache-Control")).isEqualTo("no-store");
    assertThat(resp.getHeader("Connection")).isEqualTo("keep-alive");
    assertThat(resp.getHeader("Content-Type")).contains("text/event-stream");
  }

  @Test
  void result_returnsAnalyzing_whenSinkExists() {
    QuestionAnalyzer analyzer = Mockito.mock(QuestionAnalyzer.class);
    AnalysisStreamHub hub = new AnalysisStreamHub();
    hub.getOrCreate("task-poll");

    AnalyzeController ctrl = new AnalyzeController(analyzer, hub);
    ApiResult<String> resp = ctrl.result("task-poll");

    assertThat(resp.data()).isEqualTo("ANALYZING");
  }

  @Test
  void result_returnsDone_whenSinkAbsent() {
    QuestionAnalyzer analyzer = Mockito.mock(QuestionAnalyzer.class);
    AnalysisStreamHub hub = new AnalysisStreamHub();

    AnalyzeController ctrl = new AnalyzeController(analyzer, hub);
    ApiResult<String> resp = ctrl.result("ghost");

    assertThat(resp.data()).isEqualTo("DONE");
  }

  /** Helper: 类型校验 import 不被未用 elimination · 避免 IDE 警告。 */
  @SuppressWarnings("unused")
  private void typeFixture(HttpServletResponse resp, Flux<AnalysisChunk> chunks) {
    // no-op
  }
}
