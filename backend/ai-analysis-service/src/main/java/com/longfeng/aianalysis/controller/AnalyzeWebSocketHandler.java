package com.longfeng.aianalysis.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.longfeng.aianalysis.service.AnalysisStreamHub;
import com.longfeng.common.dto.AnalysisChunk;
import java.net.URI;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

/**
 * 小程序 WebSocket 端点 · TDD §8.4 + §12.2.2 + plan §5.S3。
 *
 * <p>路径：{@code /ws/analyze/{taskId}}（实际 URI 解析见 {@link #extractTaskId}）。
 *
 * <p>D-WS · 心跳 30s · 同源 sink 与 SSE 共享：相同 taskId 的 SSE Controller + 本 Handler
 * 都消费 {@link AnalysisStreamHub} 中同一个 {@link Sinks.Many<AnalysisChunk>}（TDD §8.1 单源原则）。
 *
 * <p>客户端发 {@code "CANCEL"} 文本 → 触发 D-AI-Cancel → 调 {@link AnalysisStreamHub#dispose(String)}。
 *
 * <p>注意：实际路由由 WebSocketHandlerMapping 配置，此 Handler 默认通过 URI 末段拿 taskId。
 */
@Component("/ws/analyze")
public class AnalyzeWebSocketHandler implements WebSocketHandler {

  private static final Logger LOG = LoggerFactory.getLogger(AnalyzeWebSocketHandler.class);

  /** D-WS · 30s 心跳 ping。 */
  private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(30);

  /** D-WS · 60s 总超时（无任何消息流转则断）。 */
  private static final Duration TOTAL_TIMEOUT = Duration.ofSeconds(60);

  private final AnalysisStreamHub streamHub;
  private final ObjectMapper objectMapper;

  public AnalyzeWebSocketHandler(AnalysisStreamHub streamHub, ObjectMapper objectMapper) {
    this.streamHub = streamHub;
    this.objectMapper = objectMapper;
  }

  @Override
  public Mono<Void> handle(WebSocketSession session) {
    URI uri = session.getHandshakeInfo().getUri();
    String taskId = extractTaskId(uri);
    LOG.info("WS connected · taskId={} session={}", taskId, session.getId());

    Sinks.Many<AnalysisChunk> sink = streamHub.getOrCreate(taskId);

    Flux<WebSocketMessage> heartbeat =
        Flux.interval(HEARTBEAT_INTERVAL).map(t -> session.pingMessage(buf -> buf));

    Mono<Void> outbound =
        session
            .send(
                sink.asFlux()
                    .map(chunk -> session.textMessage(toJsonSafe(chunk)))
                    .mergeWith(heartbeat)
                    .timeout(TOTAL_TIMEOUT));

    Mono<Void> inbound =
        session
            .receive()
            .doOnNext(
                msg -> {
                  String payload = msg.getPayloadAsText();
                  if ("CANCEL".equals(payload)) {
                    LOG.info("WS CANCEL · taskId={} session={}", taskId, session.getId());
                    streamHub.dispose(taskId);
                  }
                })
            .then();

    return Mono.zip(outbound, inbound).then();
  }

  /** URI {@code /ws/analyze/{taskId}} 的最后一段是 taskId · null/空 → 给 unknown。 */
  static String extractTaskId(URI uri) {
    String path = uri == null ? null : uri.getPath();
    if (path == null || path.isBlank()) {
      return "unknown";
    }
    int idx = path.lastIndexOf('/');
    if (idx < 0 || idx == path.length() - 1) {
      return "unknown";
    }
    return path.substring(idx + 1);
  }

  /** Jackson 序列化 · 失败兜底空 JSON · 不向客户端泄漏内部错。 */
  private String toJsonSafe(AnalysisChunk chunk) {
    try {
      return objectMapper.writeValueAsString(chunk);
    } catch (JsonProcessingException jpe) {
      LOG.warn("WS chunk serialize failed · stage={} · cause={}", chunk.stage(), jpe.getMessage());
      return "{\"stage\":\"FAIL\"}";
    }
  }
}
