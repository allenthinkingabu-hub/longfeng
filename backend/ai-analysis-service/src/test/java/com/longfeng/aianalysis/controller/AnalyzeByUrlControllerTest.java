package com.longfeng.aianalysis.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.longfeng.aianalysis.controller.AnalyzeController.AnalyzeByUrlRequest;
import com.longfeng.aianalysis.llm.AnalysisResult;
import com.longfeng.aianalysis.service.AnalysisStreamHub;
import com.longfeng.aianalysis.service.QuestionAnalyzer;
import com.longfeng.common.dto.AnalysisChunk;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * WT4 · POST /api/ai/analyze-by-url 端点测试 · plan §5.S3 出口门禁。
 *
 * <p>覆盖：
 *
 * <ul>
 *   <li>合法 task_id + image_url → 202 Accepted + {"task_id":..., "status":"ANALYZING"}
 *   <li>缺失 task_id → 400
 *   <li>缺失 image_url → 400
 *   <li>调用 controller 后 streamHub.lookup(taskId) 不为空（已 pre-create sink · FE 短时间内
 *       GET /stream/{taskId} 不会 410）
 * </ul>
 */
class AnalyzeByUrlControllerTest {

  @Test
  void analyzeByUrl_validRequest_returns202_andCreatesSink() {
    QuestionAnalyzer analyzer = Mockito.mock(QuestionAnalyzer.class);
    Mockito.when(analyzer.streamAnalyze(Mockito.anyString(), Mockito.any(Resource.class), Mockito.anyString()))
        .thenReturn(Flux.empty());
    Mockito.when(analyzer.analyze(Mockito.anyString(), Mockito.any(Resource.class), Mockito.anyString()))
        .thenReturn(
            Mono.just(
                new AnalysisResult(
                    "stem", "MATH", List.of(), "OTHER", "expl", List.of(), 1, List.of())));
    AnalysisStreamHub hub = new AnalysisStreamHub();

    // Use a controller subclass that fakes URL fetch (avoid live network in unit test)
    AnalyzeController ctrl =
        new AnalyzeController(analyzer, hub) {
          @Override
          Resource fetchUrlAsResource(String imageUrl) {
            return Mockito.mock(Resource.class);
          }
        };
    AnalyzeByUrlRequest req =
        new AnalyzeByUrlRequest("tk_abc123", "MATH", "https://oss/test.jpg");

    ResponseEntity<Map<String, String>> resp = ctrl.analyzeByUrl(req);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    assertThat(resp.getBody())
        .containsEntry("task_id", "tk_abc123")
        .containsEntry("status", "ANALYZING");
    assertThat(hub.lookup("tk_abc123"))
        .as("sink should be pre-created so SSE GET doesn't 410 race")
        .isPresent();
  }

  @Test
  void analyzeByUrl_missingTaskId_returns400() {
    AnalyzeController ctrl = newCtrl();
    AnalyzeByUrlRequest req = new AnalyzeByUrlRequest("", "MATH", "https://oss/x.jpg");

    ResponseEntity<Map<String, String>> resp = ctrl.analyzeByUrl(req);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(resp.getBody()).containsKey("error");
  }

  @Test
  void analyzeByUrl_missingImageUrl_returns400() {
    AnalyzeController ctrl = newCtrl();
    AnalyzeByUrlRequest req = new AnalyzeByUrlRequest("tk_x", "MATH", "");

    ResponseEntity<Map<String, String>> resp = ctrl.analyzeByUrl(req);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void analyzeByUrl_nullSubject_defaultsToMath() {
    QuestionAnalyzer analyzer = Mockito.mock(QuestionAnalyzer.class);
    Mockito.when(analyzer.streamAnalyze(Mockito.anyString(), Mockito.any(Resource.class), Mockito.anyString()))
        .thenReturn(Flux.empty());
    AnalysisStreamHub hub = new AnalysisStreamHub();
    AnalyzeController ctrl =
        new AnalyzeController(analyzer, hub) {
          @Override
          Resource fetchUrlAsResource(String imageUrl) {
            return Mockito.mock(Resource.class);
          }
        };
    AnalyzeByUrlRequest req = new AnalyzeByUrlRequest("tk_n", null, "https://oss/x.jpg");

    ResponseEntity<Map<String, String>> resp = ctrl.analyzeByUrl(req);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    // verify subject defaulted to MATH inside async worker — give scheduler a moment
    Mockito.verify(analyzer, Mockito.timeout(2000))
        .streamAnalyze(Mockito.eq("tk_n"), Mockito.any(Resource.class), Mockito.eq("MATH"));
  }

  @Test
  void analyzeByUrl_imageFetchFails_emitsFailChunk() {
    QuestionAnalyzer analyzer = Mockito.mock(QuestionAnalyzer.class);
    AnalysisStreamHub hub = new AnalysisStreamHub();
    AnalyzeController ctrl =
        new AnalyzeController(analyzer, hub) {
          @Override
          Resource fetchUrlAsResource(String imageUrl) throws java.io.IOException {
            throw new java.io.IOException("fake fetch failure");
          }
        };
    AnalyzeByUrlRequest req =
        new AnalyzeByUrlRequest("tk_fail", "MATH", "https://oss/missing.jpg");

    java.util.List<AnalysisChunk> received = new java.util.concurrent.CopyOnWriteArrayList<>();
    hub.getOrCreate("tk_fail").asFlux().subscribe(received::add);

    ResponseEntity<Map<String, String>> resp = ctrl.analyzeByUrl(req);

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    // wait for async fail emission
    org.awaitility.Awaitility.await()
        .atMost(java.time.Duration.ofSeconds(3))
        .until(() -> !received.isEmpty());
    assertThat(received)
        .extracting(AnalysisChunk::type)
        .contains(AnalysisChunk.Type.FAIL);
  }

  private AnalyzeController newCtrl() {
    QuestionAnalyzer analyzer = Mockito.mock(QuestionAnalyzer.class);
    AnalysisStreamHub hub = new AnalysisStreamHub();
    return new AnalyzeController(analyzer, hub);
  }
}
