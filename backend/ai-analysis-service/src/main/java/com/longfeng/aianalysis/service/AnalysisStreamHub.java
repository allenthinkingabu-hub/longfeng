package com.longfeng.aianalysis.service;

import com.longfeng.common.dto.AnalysisChunk;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.Sinks.EmitFailureHandler;

/**
 * D-SSE/D-WS · {@code Map<taskId, Sinks.Many<AnalysisChunk>>} 中央转发枢纽（TDD §8.1 + plan §5.S3）。
 *
 * <p>"业务实现单源"——同一 {@link Sinks.Many} 同时被：
 *
 * <ul>
 *   <li>SSE Controller ({@code GET /api/ai/stream/{taskId}}) · H5 端
 *   <li>WebSocket Handler ({@code /ws/analyze/{taskId}}) · 小程序端
 * </ul>
 *
 * <p>多订阅者支持：用 {@code Sinks.many().multicast().onBackpressureBuffer()} ——
 * 多个客户端订阅同一 taskId 时，每人都能收到全部 chunk（关键：多设备同步 / 观察者实时）。
 *
 * <p>Sticky Session：同一 taskId 锚定到固定 pod（D-SSE）· 因此 in-memory map 足够，
 * 不需要 Redis pub/sub 跨 pod fanout（Phase 1 不引入复杂度）。
 *
 * <p>D-AI-Cancel：调 {@link #dispose(String)} 时取消订阅 + 移除 sink · {@link Disposable#dispose()}
 * 同时打断上游 LLM 调用（Spring AI ChatClient 的 reactive subscription）。
 */
@Component
public class AnalysisStreamHub {

  private static final Logger LOG = LoggerFactory.getLogger(AnalysisStreamHub.class);

  /** 主 sink 注册表 · key=taskId · multicast onBackpressureBuffer。 */
  private final Map<String, Sinks.Many<AnalysisChunk>> sinks = new ConcurrentHashMap<>();

  /** D-AI-Cancel · 关 EventSource → POST /cancel → dispose 上游 subscription。 */
  private final Map<String, Disposable> producerDisposables = new ConcurrentHashMap<>();

  /**
   * 取或创建 sink · 第一次调用时新建 · 后续相同 taskId 复用。
   *
   * @param taskId 任务 ID
   * @return multicast sink · 多个订阅者都能收到全部 chunk
   */
  public Sinks.Many<AnalysisChunk> getOrCreate(String taskId) {
    return sinks.computeIfAbsent(
        taskId,
        k -> {
          LOG.debug("AnalysisStreamHub create sink · taskId={}", k);
          return Sinks.many().multicast().onBackpressureBuffer();
        });
  }

  /**
   * 注册上游 producer 的 Disposable · D-AI-Cancel 时一并 dispose。
   *
   * @param taskId           任务 ID
   * @param producerSubscription 上游 LLM 调用产生的 reactive subscription
   */
  public void registerProducer(String taskId, Disposable producerSubscription) {
    producerDisposables.put(taskId, producerSubscription);
  }

  /**
   * D-AI-Cancel 入口 · 取消上游订阅 + 推 CANCELLED 事件 + 完成 sink + 清理资源。
   *
   * <p>注意："已经付费给供应商的 token 不退" —— LLM 调用已发出的 chunk 仍会被消费，
   * 但 dispose 后不再产生新 chunk · sink complete 后 SSE/WS 客户端收到 {@code complete} 事件。
   *
   * <p><b>WT4 · 2026-05-04</b>：在 complete 之前先 emit 一条 CANCELLED chunk · 让 FE
   * {@code useEventSource.ts} 通过 JSON {@code type:"CANCELLED"} 触发 onCancelled 回调
   * · 否则 FE 只能通过 fetch 自然结束被动判定 · 体验差。
   *
   * @param taskId 任务 ID
   * @return true 若找到并取消 · false 若 taskId 不存在
   */
  public boolean dispose(String taskId) {
    boolean found = false;
    Disposable producer = producerDisposables.remove(taskId);
    if (producer != null && !producer.isDisposed()) {
      producer.dispose();
      found = true;
      LOG.info("D-AI-Cancel · producer disposed · taskId={}", taskId);
    }
    Sinks.Many<AnalysisChunk> sink = sinks.remove(taskId);
    if (sink != null) {
      // WT4 · FE 期望 type:"CANCELLED" 终结事件
      sink.tryEmitNext(AnalysisChunk.cancelled());
      sink.tryEmitComplete();
      found = true;
    }
    return found;
  }

  /** Alias of {@link #dispose(String)} preserved for callers that already use {@code cancel(...)}. */
  public boolean cancel(String taskId) {
    return dispose(taskId);
  }

  /** 查询 sink 是否存在（fallback polling 端点用 · TDD §8.7）。 */
  public Optional<Sinks.Many<AnalysisChunk>> lookup(String taskId) {
    return Optional.ofNullable(sinks.get(taskId));
  }

  /**
   * 推送 chunk · sink 不存在时静默（OCR 完成时 sink 一定已存在）。
   *
   * @param taskId 任务 ID
   * @param chunk  chunk payload
   */
  public void emit(String taskId, AnalysisChunk chunk) {
    Sinks.Many<AnalysisChunk> sink = sinks.get(taskId);
    if (sink == null) {
      LOG.debug("emit skipped · sink missing · taskId={}", taskId);
      return;
    }
    sink.emitNext(chunk, EmitFailureHandler.FAIL_FAST);
  }

  /** 当前活跃 task 数（监控指标）。 */
  public int activeTaskCount() {
    return sinks.size();
  }
}
