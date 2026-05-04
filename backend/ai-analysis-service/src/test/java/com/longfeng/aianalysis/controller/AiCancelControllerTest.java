package com.longfeng.aianalysis.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.longfeng.aianalysis.service.AnalysisStreamHub;
import com.longfeng.common.dto.AnalysisChunk;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Sinks;

/**
 * D-AI-Cancel 端点测试 · plan §5.S3 出口门禁。
 *
 * <p>覆盖（WT4 重构）：cancel 已知 taskId 返 200 + {"status":"CANCELLED"} · cancel 未知 taskId
 * （幂等同样返 200）· dispose 调上游 producer · sink emit CANCELLED chunk before complete。
 */
class AiCancelControllerTest {

  @Test
  void cancel_existingTask_returns200WithCancelledStatus() {
    AnalysisStreamHub hub = new AnalysisStreamHub();
    Sinks.Many<AnalysisChunk> ignored = hub.getOrCreate("task-known");

    AiCancelController ctrl = new AiCancelController(hub);
    ResponseEntity<Map<String, String>> resp = ctrl.cancel("task-known");

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(resp.getBody()).containsEntry("status", "CANCELLED");
    assertThat(hub.lookup("task-known")).isEmpty();
  }

  @Test
  void cancel_unknownTask_stillReturns200_idempotent() {
    AnalysisStreamHub hub = new AnalysisStreamHub();
    AiCancelController ctrl = new AiCancelController(hub);
    ResponseEntity<Map<String, String>> resp = ctrl.cancel("never-existed");

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(resp.getBody()).containsEntry("status", "CANCELLED");
  }

  @Test
  void cancel_disposesRegisteredProducer() {
    AnalysisStreamHub hub = new AnalysisStreamHub();
    hub.getOrCreate("task-prod");
    boolean[] disposed = {false};
    hub.registerProducer(
        "task-prod",
        new reactor.core.Disposable() {
          @Override
          public void dispose() {
            disposed[0] = true;
          }

          @Override
          public boolean isDisposed() {
            return disposed[0];
          }
        });

    AiCancelController ctrl = new AiCancelController(hub);
    ctrl.cancel("task-prod");

    assertThat(disposed[0]).as("D-AI-Cancel · upstream producer must dispose").isTrue();
  }

  @Test
  void cancel_emitsCancelledChunkBeforeComplete() {
    AnalysisStreamHub hub = new AnalysisStreamHub();
    Sinks.Many<AnalysisChunk> sink = hub.getOrCreate("task-cancel-chunk");
    java.util.List<AnalysisChunk> received = new java.util.concurrent.CopyOnWriteArrayList<>();
    java.util.concurrent.atomic.AtomicBoolean completed =
        new java.util.concurrent.atomic.AtomicBoolean();
    sink.asFlux().subscribe(received::add, ex -> {}, () -> completed.set(true));

    new AiCancelController(hub).cancel("task-cancel-chunk");

    // FE 期望: type=CANCELLED 终结事件
    assertThat(received)
        .extracting(AnalysisChunk::type)
        .contains(AnalysisChunk.Type.CANCELLED);
    assertThat(completed).isTrue();
  }
}
