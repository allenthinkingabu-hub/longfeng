package com.longfeng.aianalysis.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.longfeng.common.dto.AnalysisChunk;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.core.Disposable;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

/**
 * Plan §5.S3 出口门禁：multicast sink + 多订阅者 + D-AI-Cancel dispose 关键路径。
 */
class AnalysisStreamHubTest {

  @Test
  void getOrCreate_sameTaskId_returnsSameSink() {
    AnalysisStreamHub hub = new AnalysisStreamHub();
    Sinks.Many<AnalysisChunk> a = hub.getOrCreate("task-1");
    Sinks.Many<AnalysisChunk> b = hub.getOrCreate("task-1");
    assertThat(a).isSameAs(b);
  }

  @Test
  void multipleSubscribers_allReceiveAllChunks() {
    AnalysisStreamHub hub = new AnalysisStreamHub();
    Sinks.Many<AnalysisChunk> sink = hub.getOrCreate("task-multi");

    List<AnalysisChunk> r1 = new ArrayList<>();
    List<AnalysisChunk> r2 = new ArrayList<>();

    Disposable s1 = sink.asFlux().subscribe(r1::add);
    Disposable s2 = sink.asFlux().subscribe(r2::add);

    sink.tryEmitNext(AnalysisChunk.ocr());
    sink.tryEmitNext(AnalysisChunk.analysis("loading"));
    sink.tryEmitNext(AnalysisChunk.done("result"));
    sink.tryEmitComplete();

    s1.dispose();
    s2.dispose();

    assertThat(r1).hasSize(3);
    assertThat(r2).hasSize(3);
    assertThat(r1.get(0).stage()).isEqualTo(AnalysisChunk.Stage.OCR);
    assertThat(r1.get(2).stage()).isEqualTo(AnalysisChunk.Stage.DONE);
  }

  @Test
  void dispose_cancelsProducerAndCompletesSink() {
    AnalysisStreamHub hub = new AnalysisStreamHub();
    Sinks.Many<AnalysisChunk> sink = hub.getOrCreate("task-cancel");

    boolean[] producerDisposed = {false};
    Disposable producerStub =
        new Disposable() {
          @Override public void dispose() { producerDisposed[0] = true; }
          @Override public boolean isDisposed() { return producerDisposed[0]; }
        };
    hub.registerProducer("task-cancel", producerStub);

    StepVerifier.create(sink.asFlux())
        .then(() -> hub.dispose("task-cancel"))
        .verifyComplete();

    assertThat(producerDisposed[0]).isTrue();
    assertThat(hub.lookup("task-cancel")).isEmpty();
  }

  @Test
  void dispose_unknownTaskId_returnsFalse() {
    AnalysisStreamHub hub = new AnalysisStreamHub();
    assertThat(hub.dispose("non-existing")).isFalse();
  }

  @Test
  void emit_withoutSink_isSilentNoOp() {
    AnalysisStreamHub hub = new AnalysisStreamHub();
    // 不创建 sink 直接 emit · 应 silent skip 而不是 NPE
    hub.emit("ghost", AnalysisChunk.ocr());
    assertThat(hub.activeTaskCount()).isZero();
  }

  @Test
  void activeTaskCount_reflectsHubState() {
    AnalysisStreamHub hub = new AnalysisStreamHub();
    hub.getOrCreate("t1");
    hub.getOrCreate("t2");
    hub.getOrCreate("t3");
    assertThat(hub.activeTaskCount()).isEqualTo(3);

    hub.dispose("t2");
    assertThat(hub.activeTaskCount()).isEqualTo(2);
  }

  @Test
  void lookup_returnsOptional() {
    AnalysisStreamHub hub = new AnalysisStreamHub();
    hub.getOrCreate("present");
    assertThat(hub.lookup("present")).isPresent();
    assertThat(hub.lookup("absent")).isEmpty();
  }

  @SuppressWarnings("unused")
  private static Duration unusedHelper() {
    return Duration.ofSeconds(1);
  }
}
