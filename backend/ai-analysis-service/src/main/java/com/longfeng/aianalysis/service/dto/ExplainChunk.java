package com.longfeng.aianalysis.service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * SSE frame payload for {@code GET /analysis/{itemId}/stream} (G-01 decision · be-build-spec
 * sse_protocol.chunk_shape). Each chunk carries a text fragment plus an optional terminal flag:
 *
 * <ul>
 *   <li>chunk — text fragment ({@code wrong_item_analysis.error_reason} block, or notice text)
 *   <li>done — {@code true} only on the terminal frame so the browser can close EventSource;
 *       intermediate frames omit the field (NON_NULL filter) per the agreed wire shape
 * </ul>
 *
 * <p>This DTO is service-layer transport only — the controller wraps a list of these into
 * SseEmitter writes (BR-16: no MQ side-channel, only replay of already-persisted analysis).
 */
@Schema(description = "SSE 帧 payload · GET /analysis/{itemId}/stream")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExplainChunk(
    @Schema(description = "文本片段") @JsonProperty("chunk") String chunk,
    @Schema(description = "终帧标识 · true 时前端应关闭 EventSource · 中间帧省略此字段") @JsonProperty("done") Boolean done) {

  /** Convenience factory for an intermediate (non-terminal) frame. */
  public static ExplainChunk of(String chunk) {
    return new ExplainChunk(chunk, null);
  }

  /** Convenience factory for the terminal frame (chunk text + done:true). */
  public static ExplainChunk terminal(String chunk) {
    return new ExplainChunk(chunk, Boolean.TRUE);
  }
}
