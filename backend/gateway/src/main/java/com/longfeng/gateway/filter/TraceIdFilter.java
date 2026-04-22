package com.longfeng.gateway.filter;

import java.util.Optional;
import java.util.UUID;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Gateway trace-id filter (order = -200) · 落地计划 §6.7 Step 7.
 *
 * <p>Runs first in the chain so JWT errors / rate-limit 429s carry {@code X-Trace-Id}. Propagates
 * header downstream and writes back on response.
 */
@Component
public class TraceIdFilter implements GlobalFilter, Ordered {

  public static final String HEADER = "X-Trace-Id";
  public static final int ORDER = -200;

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    String traceId =
        Optional.ofNullable(exchange.getRequest().getHeaders().getFirst(HEADER))
            .filter(s -> !s.isBlank())
            .orElse(UUID.randomUUID().toString());
    exchange.getResponse().getHeaders().set(HEADER, traceId);
    ServerHttpRequest mutated =
        exchange.getRequest().mutate().header(HEADER, traceId).build();
    ServerWebExchange mutatedEx = exchange.mutate().request(mutated).build();
    mutatedEx.getAttributes().put("traceId", traceId);
    return chain.filter(mutatedEx).contextWrite(ctx -> ctx.put("traceId", traceId));
  }

  @Override
  public int getOrder() {
    return -200;
  }
}
