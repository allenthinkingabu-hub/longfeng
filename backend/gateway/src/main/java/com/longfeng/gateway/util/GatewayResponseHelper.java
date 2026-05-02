package com.longfeng.gateway.util;

import com.longfeng.gateway.tmp.ErrCode;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Shared helper for writing structured error JSON responses in gateway filters.
 *
 * <p>All error bodies use the unified API envelope: {@code {"code":XXXXX,"message":"...",
 * "data":null,"traceId":"..."}}.
 */
public final class GatewayResponseHelper {

  private GatewayResponseHelper() {}

  /**
   * Write an error response with status derived from {@link ErrCode} and return a completed Mono.
   */
  public static Mono<Void> error(ServerWebExchange exchange, ErrCode errCode) {
    return writeError(exchange, errCode.httpStatus(), errCode.code(), errCode.msgkey());
  }

  /** Write an error response with explicit HTTP status, code and message. */
  public static Mono<Void> writeError(
      ServerWebExchange exchange, int httpStatus, int code, String message) {
    String traceId = exchange.getAttribute("traceId");
    if (traceId == null) {
      traceId = exchange.getRequest().getHeaders().getFirst("X-Trace-Id");
    }
    String body =
        String.format(
            "{\"code\":%d,\"message\":\"%s\",\"data\":null,\"traceId\":\"%s\"}",
            code, message, traceId == null ? "" : traceId);

    exchange.getResponse().setStatusCode(HttpStatus.resolve(httpStatus));
    exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
    DataBuffer buffer =
        exchange.getResponse().bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
    return exchange.getResponse().writeWith(Mono.just(buffer));
  }
}
