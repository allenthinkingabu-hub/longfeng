package com.longfeng.gateway.filter;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import java.util.Optional;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * IP / user-scoped rate limiter (order = -60) · 落地计划 §6.7 Step 7.
 *
 * <p>Key precedence: {@code X-User-Id} (injected by {@link JwtAuthFilter}) → remote address.
 * Actuator / api-docs paths are exempt so probes and OpenAPI polling never 429.
 */
@Component
public class RateLimitFilter implements GlobalFilter, Ordered {

  public static final int ORDER = -60;

  private final RateLimiterRegistry registry;

  public RateLimitFilter(RateLimiterRegistry registry) {
    this.registry = registry;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    String path = exchange.getRequest().getURI().getPath();
    if (path.startsWith("/actuator/")
        || path.startsWith("/v3/api-docs")
        || path.startsWith("/swagger-ui")) {
      return chain.filter(exchange);
    }
    String key =
        Optional.ofNullable(exchange.getRequest().getHeaders().getFirst("X-User-Id"))
            .or(
                () ->
                    Optional.ofNullable(exchange.getRequest().getRemoteAddress())
                        .map(addr -> addr.getAddress().getHostAddress()))
            .orElse("anon");
    RateLimiter limiter = registry.rateLimiter("gw-" + key, "default");
    if (!limiter.acquirePermission()) {
      exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
      return exchange.getResponse().setComplete();
    }
    return chain.filter(exchange);
  }

  @Override
  public int getOrder() {
    return -60;
  }
}
