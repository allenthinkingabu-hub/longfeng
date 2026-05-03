package com.longfeng.aianalysis.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.longfeng.aianalysis.service.AnalysisStreamHub;
import com.longfeng.common.dto.AnalysisChunk;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Sinks;

/**
 * D-AI-Cancel 端点测试 · plan §5.S3 出口门禁。
 *
 * <p>覆盖：cancel 已知 taskId · cancel 未知 taskId（幂等 204）· dispose 调上游 producer。
 */
class AiCancelControllerTest {

  @Test
  void cancel_existingTask_returns204AndDisposes() {
    AnalysisStreamHub hub = new AnalysisStreamHub();
    Sinks.Many<AnalysisChunk> sink = hub.getOrCreate("task-known");

    AiCancelController ctrl = new AiCancelController(hub);
    ResponseEntity<Void> resp = ctrl.cancel("task-known");

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(hub.lookup("task-known")).isEmpty();
  }

  @Test
  void cancel_unknownTask_stillReturns204_idempotent() {
    AnalysisStreamHub hub = new AnalysisStreamHub();
    AiCancelController ctrl = new AiCancelController(hub);
    ResponseEntity<Void> resp = ctrl.cancel("never-existed");

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
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
}
